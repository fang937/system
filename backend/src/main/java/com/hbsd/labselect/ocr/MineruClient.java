package com.hbsd.labselect.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 精准解析客户端（mineru.net v4）。
 *
 * <p>v4 全部是异步任务，本地文件走「预签名上传」三步：</p>
 * <pre>
 * POST {root}/file-urls/batch        {"files":[{"name":"简历.pdf","is_ocr":true}],"model_version":"vlm"}
 *   → {"code":0,"data":{"batch_id":"...","file_urls":["https://..."]}}
 * PUT  {file_urls[0]}                原始文件字节（不要带 Content-Type），上传完成即自动提交解析
 * GET  {root}/extract-results/batch/{batch_id}
 *   → data.extract_result[0].state: waiting-file | pending | running | converting | done | failed
 *   → done 时取 full_zip_url，zip 内 full.md 即解析出的 Markdown
 * </pre>
 *
 * <p>相比 PP-StructureV3，MinerU 的 vlm 模型对复杂简历版式（多栏、表格）识别更稳，
 * 且不需要自己处理 base64 与分页；代价是要经过一次对象存储上传。</p>
 */
@Slf4j
public class MineruClient {

    /** 轮询间隔：MinerU 通常 10~60 秒出结果 */
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 上传申请后等待对象存储就绪的时间 */
    private static final int ZIP_MAX_BYTES = 60 * 1024 * 1024;

    private final OcrProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public MineruClient(OcrProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** 解析本地文件，返回 Markdown 正文。 */
    public String parse(Path path) {
        String batchId = upload(path);
        log.info("MinerU 上传完成，batchId={}", batchId);
        String zipUrl = poll(batchId);
        return readZip(zipUrl);
    }

    /* ---------------- 申请上传链接 + PUT ---------------- */

    private String upload(Path path) {
        String root = root();
        String fileName = path.getFileName().toString();

        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileName);
        file.put("is_ocr", properties.isMineruForceOcr());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("files", List.of(file));
        body.put("model_version", properties.getMineruModel());
        body.put("enable_formula", false);
        body.put("enable_table", true);
        body.put("language", "ch");

        JsonNode data = call("POST", root + "/file-urls/batch", body);
        String batchId = data.path("batch_id").asText("");
        JsonNode urls = data.path("file_urls");
        if (batchId.isBlank() || !urls.isArray() || urls.isEmpty()) {
            throw new BizException(502, "MinerU 未返回上传地址：" + data.toString());
        }
        String uploadUrl = urls.get(0).asText("");

        // PUT 上传：预签名地址要求不带 Content-Type，否则签名校验失败
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(Duration.ofSeconds(Math.min(120, properties.getMineruTimeoutSeconds())))
                    .PUT(HttpRequest.BodyPublishers.ofFile(path))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, "MinerU 文件上传失败 " + response.statusCode());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "MinerU 文件上传异常：" + e.getMessage());
        }
        return batchId;
    }

    /* ---------------- 轮询批量结果 ---------------- */

    private String poll(String batchId) {
        String url = root() + "/extract-results/batch/" + batchId;
        long deadline = System.currentTimeMillis() + properties.getMineruTimeoutSeconds() * 1000L;
        String lastState = "";

        while (System.currentTimeMillis() < deadline) {
            try {
                JsonNode data = call("GET", url, null);
                JsonNode results = data.path("extract_result");
                if (results.isArray() && !results.isEmpty()) {
                    JsonNode first = results.get(0);
                    String state = first.path("state").asText("");
                    if (!state.equals(lastState)) {
                        log.info("MinerU batch {} 状态 -> {}", batchId, state);
                        lastState = state;
                    }
                    if ("done".equalsIgnoreCase(state)) {
                        String zipUrl = first.path("full_zip_url").asText("");
                        if (zipUrl.isBlank()) {
                            throw new BizException(502, "MinerU 解析完成但没有结果地址");
                        }
                        return zipUrl;
                    }
                    if ("failed".equalsIgnoreCase(state)) {
                        throw new BizException(502, "MinerU 解析失败：" + first.path("err_msg").asText("未知原因"));
                    }
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("MinerU 轮询异常：{}", e.getMessage());
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(502, "MinerU 解析被中断");
            }
        }
        throw new BizException(502, "MinerU 解析超时（batchId=" + batchId + "，最后状态 " + lastState + "）");
    }

    /* ---------------- 下载 zip 取 full.md ---------------- */

    private String readZip(String zipUrl) {
        byte[] bytes;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(zipUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, "MinerU 结果下载返回 " + response.statusCode());
            }
            bytes = response.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "MinerU 结果下载失败：" + e.getMessage());
        }
        if (bytes == null || bytes.length == 0) {
            throw new BizException(502, "MinerU 结果包为空");
        }
        if (bytes.length > ZIP_MAX_BYTES) {
            throw new BizException(502, "MinerU 结果包过大：" + bytes.length);
        }

        List<String> markdowns = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith("full.md") || name.equals("full.md")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zis.transferTo(out);
                    markdowns.add(out.toString(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            throw new BizException(502, "MinerU 结果包无法解压：" + e.getMessage());
        }

        if (markdowns.isEmpty()) {
            throw new BizException(502, "MinerU 结果包中没有 full.md");
        }
        String markdown = String.join("\n\n", markdowns).trim();
        log.info("MinerU 解析完成，Markdown {} 字", markdown.length());
        return markdown;
    }

    /* ---------------- 通用调用 ---------------- */

    private JsonNode call(String method, String url, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + properties.getMineruToken())
                    .header("Accept", "*/*");

            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = response.body();
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, "MinerU 接口返回 " + response.statusCode() + "：" + snippet(text));
            }
            JsonNode root = mapper.readTree(text);
            int code = root.path("code").asInt(0);
            if (code != 0) {
                throw new BizException(502, "MinerU 调用失败：" + root.path("msg").asText("未知错误")
                        + "（code=" + code + "）");
            }
            return root.path("data");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "MinerU 调用异常：" + e.getMessage());
        }
    }

    private String root() {
        String endpoint = properties.getMineruEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BizException(502, "MinerU 未配置接口地址");
        }
        return endpoint.replaceAll("/+$", "");
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
