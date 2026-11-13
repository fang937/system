package com.hbsd.labselect.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PP-StructureV3 文档解析客户端（PaddleOCR 3.x 的版面分析 + 表格识别 + 阅读顺序还原）。
 *
 * <p>相比逐页整图 OCR，它的产出是带结构的 Markdown：标题层级、列表、表格、阅读顺序都是对的，
 * 简历里的「教育经历 / 项目 / 技能」不会被揉成一串字，对后续 AI 初评更有用。</p>
 *
 * <p>支持两种服务形态，按 endpoint 自动识别：</p>
 *
 * <ol>
 *   <li><b>异步 Job 模式</b>（飞桨星河社区 paddleocr.aistudio-app.com）——endpoint 含 {@code /jobs}：
 *       <pre>
 * POST {endpoint}                 multipart: file + model + optionalPayload
 * 200  {"data":{"jobId":"xxx"}}
 * GET  {endpoint}/{jobId}         轮询 state: pending | running | done | failed
 * done → data.resultUrl.jsonUrl → 逐行 JSON → result.layoutParsingResults[].markdown.text
 *       </pre>
 *   </li>
 *   <li><b>同步模式</b>（PaddleX 本地服务 /layout-parsing）——base64 直传，一次返回结果。</li>
 * </ol>
 */
@Slf4j
public class PpStructureV3Client {

    private static final String CRLF = "\r\n";
    /** 轮询间隔，星河社区一般 10~40 秒出结果，2 秒一次不会给服务端压力 */
    private static final long POLL_INTERVAL_MS = 2000L;

    private final OcrProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public PpStructureV3Client(OcrProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, properties.getStructureTimeoutSeconds())))
                .build();
    }

    /** 整份 PDF 直接交给服务端解析，返回 Markdown 正文。 */
    public ParseResult parsePdf(Path path) {
        if (jobMode()) {
            return parsePdfByJob(path);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return parse(Base64.getEncoder().encodeToString(bytes), 0);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PP-StructureV3 读取 PDF 失败：{}", e.getMessage());
            throw new BizException(502, "简历读取失败：" + e.getMessage());
        }
    }

    /** 单张图片（服务不可用时的逐页兜底）。 */
    public ParseResult parseImage(byte[] image) {
        return parse(Base64.getEncoder().encodeToString(image), 1);
    }

    /** endpoint 指向异步任务接口时走 Job 模式 */
    private boolean jobMode() {
        String endpoint = properties.getStructureEndpoint();
        return endpoint != null && endpoint.toLowerCase(Locale.ROOT).contains("/jobs");
    }

    /* ---------------- 异步 Job 模式 ---------------- */

    private ParseResult parsePdfByJob(Path path) {
        String jobId = submitJob(path);
        log.info("PP-StructureV3 已提交解析任务 jobId={}", jobId);
        String jsonUrl = pollJob(jobId);
        return readJsonl(jsonUrl);
    }

    private String submitJob(Path path) {
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(path);
        } catch (Exception e) {
            throw new BizException(502, "简历读取失败：" + e.getMessage());
        }

        String boundary = "----labselect" + System.currentTimeMillis();
        Map<String, Object> optional = new LinkedHashMap<>();
        optional.put("useDocOrientationClassify", false);
        optional.put("useDocUnwarping", false);
        optional.put("useChartRecognition", properties.isStructureTable());
        optional.put("useTableRecognition", properties.isStructureTable());
        optional.put("useFormulaRecognition", properties.isStructureFormula());

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            writeField(body, boundary, "model", "PP-StructureV3");
            writeField(body, boundary, "optionalPayload", mapper.writeValueAsString(optional));
            writeFile(body, boundary, "file", path.getFileName().toString(), contentTypeOf(path), fileBytes);
            body.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException(502, "简历上传体构造失败：" + e.getMessage());
        }

        HttpResponse<String> response;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getStructureEndpoint()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            applyAuth(builder);
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("PP-StructureV3 提交失败：{}", e.getMessage());
            throw new BizException(502, "文档解析服务不可用：" + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            throw new BizException(502, "文档解析提交返回 " + response.statusCode() + "：" + snippet(response.body()));
        }
        try {
            String jobId = mapper.readTree(response.body()).path("data").path("jobId").asText("");
            if (jobId.isBlank()) {
                throw new BizException(502, "文档解析未返回 jobId：" + snippet(response.body()));
            }
            return jobId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "文档解析提交结果无法识别：" + e.getMessage());
        }
    }

    /** 轮询任务状态，返回结果 jsonl 地址；超时或失败抛异常，由上层降级。 */
    private String pollJob(String jobId) {
        String endpoint = properties.getStructureEndpoint().replaceAll("/+$", "");
        long deadline = System.currentTimeMillis() + properties.getStructureTimeoutSeconds() * 1000L;
        String lastState = "";

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "/" + jobId))
                        .timeout(Duration.ofSeconds(30))
                        .GET();
                applyAuth(builder);
                HttpResponse<String> response = http.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() / 100 == 2) {
                    JsonNode data = mapper.readTree(response.body()).path("data");
                    String state = data.path("state").asText("");
                    if (!state.equals(lastState)) {
                        log.info("PP-StructureV3 job {} 状态 -> {}", jobId, state);
                        lastState = state;
                    }
                    if ("done".equalsIgnoreCase(state)) {
                        String jsonUrl = data.path("resultUrl").path("jsonUrl").asText("");
                        if (jsonUrl.isBlank()) {
                            throw new BizException(502, "文档解析完成但未返回结果地址");
                        }
                        return jsonUrl;
                    }
                    if ("failed".equalsIgnoreCase(state)) {
                        throw new BizException(502, "文档解析失败：" + data.path("errorMsg").asText("未知原因"));
                    }
                } else {
                    log.warn("PP-StructureV3 查询 job {} 返回 {}", jobId, response.statusCode());
                }
            } catch (BizException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(502, "文档解析被中断");
            } catch (Exception e) {
                log.warn("PP-StructureV3 轮询异常：{}", e.getMessage());
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(502, "文档解析被中断");
            }
        }
        throw new BizException(502, "文档解析超时（jobId=" + jobId + "，最后状态 " + lastState + "）");
    }

    /** 下载 jsonl，每行一个 result，累加各页 markdown。 */
    private ParseResult readJsonl(String jsonUrl) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(jsonUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException(502, "文档解析结果下载失败：" + e.getMessage());
        }
        if (response.statusCode() / 100 != 2) {
            throw new BizException(502, "文档解析结果下载返回 " + response.statusCode());
        }

        List<String> blocks = new ArrayList<>();
        int pages = 0;
        try {
            for (String line : response.body().split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode result = mapper.readTree(line).path("result");
                for (JsonNode page : result.path("layoutParsingResults")) {
                    pages++;
                    String markdown = page.path("markdown").path("text").asText("");
                    if (!markdown.isBlank()) {
                        blocks.add(markdown);
                    }
                }
            }
        } catch (Exception e) {
            throw new BizException(502, "文档解析结果无法识别：" + e.getMessage());
        }

        if (blocks.isEmpty()) {
            throw new BizException(502, "文档解析未返回正文，请确认上传的是可识别的 PDF");
        }
        String markdown = String.join("\n\n", blocks).trim();
        log.info("PP-StructureV3 解析 {} 页，得到 {} 字 Markdown", pages, markdown.length());
        return new ParseResult(markdown, Math.max(1, pages));
    }

    /* ---------------- 同步模式 ---------------- */

    private ParseResult parse(String base64, int fileType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file", base64);
        body.put("fileType", fileType);
        body.put("useDocOrientationClassify", false);
        body.put("useDocUnwarping", false);
        body.put("useTableRecognition", properties.isStructureTable());
        body.put("useFormulaRecognition", properties.isStructureFormula());
        body.put("useSealRecognition", false);
        body.put("useChartRecognition", false);

        HttpResponse<String> response;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getStructureEndpoint()))
                    .timeout(Duration.ofSeconds(properties.getStructureTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            applyAuth(builder);
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("PP-StructureV3 调用失败：{}", e.getMessage());
            throw new BizException(502, "文档解析服务不可用：" + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            throw new BizException(502, "文档解析返回 " + response.statusCode() + "：" + snippet(response.body()));
        }
        try {
            return read(mapper.readTree(response.body()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "文档解析结果无法识别：" + e.getMessage());
        }
    }

    private ParseResult read(JsonNode root) {
        int errorCode = root.path("errorCode").asInt(0);
        if (errorCode != 0) {
            String message = root.path("errorMsg").asText("未知错误");
            throw new BizException(502, "文档解析失败：" + message + "（errorCode=" + errorCode + "）");
        }

        JsonNode pages = root.path("result").path("layoutParsingResults");
        if (!pages.isArray()) {
            pages = root.path("layoutParsingResults");
        }
        List<String> blocks = new ArrayList<>();
        int count = 0;
        for (JsonNode page : pages) {
            count++;
            String markdown = page.path("markdown").path("text").asText("");
            if (!markdown.isBlank()) {
                blocks.add(markdown);
                continue;
            }
            // 兜底 1：PaddleX 的 parsing_res_list[].block_content
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : page.path("prunedResult").path("parsing_res_list")) {
                String content = block.path("block_content").asText("");
                if (!content.isBlank()) {
                    sb.append(content).append('\n');
                }
            }
            // 兜底 2：纯识别文本
            if (sb.length() == 0) {
                for (JsonNode text : page.path("prunedResult").path("overall_ocr_res").path("rec_texts")) {
                    if (!text.asText("").isBlank()) {
                        sb.append(text.asText()).append('\n');
                    }
                }
            }
            if (sb.length() > 0) {
                blocks.add(sb.toString());
            }
        }
        // 兜底 3：简化封装 {"texts": [...]}
        if (blocks.isEmpty()) {
            for (JsonNode text : root.path("texts")) {
                if (!text.asText("").isBlank()) {
                    blocks.add(text.asText());
                }
            }
        }
        if (blocks.isEmpty()) {
            throw new BizException(502, "文档解析未返回正文，请确认上传的是可识别的 PDF");
        }
        String markdown = String.join("\n\n", blocks).trim();
        log.info("PP-StructureV3 解析 {} 页，得到 {} 字 Markdown", count == 0 ? 1 : count, markdown.length());
        return new ParseResult(markdown, count == 0 ? 1 : count);
    }

    /* ---------------- 工具 ---------------- */

    private void applyAuth(HttpRequest.Builder builder) {
        String key = properties.structureKey();
        if (key == null || key.isBlank()) {
            return;
        }
        String scheme = properties.getStructureAuthScheme();
        if (scheme == null || scheme.isBlank() || "none".equalsIgnoreCase(scheme)) {
            builder.header("Authorization", key);
        } else {
            builder.header("Authorization", scheme.trim() + " " + key);
        }
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write((value + CRLF).getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(ByteArrayOutputStream out, String boundary, String name, String fileName,
                           String contentType, byte[] content) throws Exception {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"" + CRLF)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private String contentTypeOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /** 解析结果：Markdown 正文 + 页数。 */
    public record ParseResult(String markdown, int pages) {
    }
}
