package com.hbsd.labselect.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import lombok.extern.slf4j.Slf4j;

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
import java.util.Map;

/**
 * PP-StructureV3 文档解析客户端（PaddleOCR 3.x 的版面分析 + 表格识别 + 阅读顺序还原）。
 *
 * <p>相比逐页整图 OCR，它的产出是带结构的 Markdown：标题层级、列表、表格、阅读顺序都是对的，
 * 简历里的「教育经历 / 项目 / 技能」不会被揉成一串字，对后续 AI 初评更有用。
 * 而且支持直接传 PDF（fileType=0），服务端自己拆页，本地不用再渲染图片。</p>
 *
 * <pre>
 * POST {structure-endpoint}
 * {"file": "&lt;base64&gt;", "fileType": 0, "useTableRecognition": true}
 * 200 {"errorCode":0,"result":{"layoutParsingResults":[{"markdown":{"text":"# 张三 ..."}}]}}
 * </pre>
 *
 * <p>同时兼容 PaddleX 本地服务、飞桨星河社区在线服务（Authorization: token）以及
 * 部分版本的 {"results":[...]} 封装。</p>
 */
@Slf4j
public class PpStructureV3Client {

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
            String key = properties.structureKey();
            if (key != null && !key.isBlank()) {
                String scheme = properties.getStructureAuthScheme();
                if (scheme == null || scheme.isBlank() || "none".equalsIgnoreCase(scheme)) {
                    builder.header("Authorization", key);
                } else {
                    builder.header("Authorization", scheme.trim() + " " + key);
                }
            }
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
