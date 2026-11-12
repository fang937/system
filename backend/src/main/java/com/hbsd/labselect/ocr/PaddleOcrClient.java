package com.hbsd.labselect.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PaddleOCR HTTP 客户端。
 *
 * <p>默认按 PaddleOCR hubserving 协议调用：</p>
 * <pre>
 * POST {endpoint}
 * {"images": ["&lt;base64 png&gt;", "..."]}
 * 200 {"results":[{"data":[{"text":"识别文本","confidence":0.98}]}]}
 * </pre>
 *
 * <p>同时兼容 PaddleX 3.x 的返回（result.ocrResults[].prunedResult.rec_texts）与
 * 简化的 {"texts":[...]}，方便换用不同部署方式。</p>
 */
@Slf4j
public class PaddleOcrClient {

    private final OcrProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public PaddleOcrClient(OcrProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, properties.getTimeoutSeconds())))
                .build();
    }

    /** 识别多张图片，返回按顺序拼接的文本。 */
    public String recognize(List<byte[]> images) {
        if (images.isEmpty()) {
            return "";
        }
        List<String> encoded = new ArrayList<>();
        for (byte[] image : images) {
            encoded.add(Base64.getEncoder().encodeToString(image));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("images", encoded);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + properties.getApiKey());
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, "PaddleOCR 返回 " + response.statusCode() + "：" + snippet(response.body()));
            }
            String text = parse(mapper.readTree(response.body()));
            log.info("PaddleOCR 识别 {} 页，得到 {} 字", images.size(), text.length());
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PaddleOCR 调用失败：{}", e.getMessage());
            throw new BizException(502, "PaddleOCR 调用失败：" + e.getMessage());
        }
    }

    private String parse(com.fasterxml.jackson.databind.JsonNode root) {
        List<String> lines = new ArrayList<>();
        // hubserving: results[].data[].text
        for (var page : root.path("results")) {
            for (var item : page.path("data")) {
                String text = item.path("text").asText("");
                if (!text.isBlank()) {
                    lines.add(text);
                }
            }
        }
        // PaddleX: result.ocrResults[].prunedResult.rec_texts[]
        for (var item : root.path("result").path("ocrResults")) {
            for (var text : item.path("prunedResult").path("rec_texts")) {
                if (!text.asText("").isBlank()) {
                    lines.add(text.asText());
                }
            }
        }
        // 简化协议: texts[]
        for (var text : root.path("texts")) {
            if (!text.asText("").isBlank()) {
                lines.add(text.asText());
            }
        }
        if (lines.isEmpty()) {
            collect(root, lines, 0);
        }
        return String.join("\n", lines);
    }

    /** 兜底：递归找 text / rec_texts 字段，兼容自定义封装。 */
    private void collect(com.fasterxml.jackson.databind.JsonNode node, List<String> lines, int depth) {
        if (node == null || depth > 8) {
            return;
        }
        if (node.isObject()) {
            var text = node.get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                lines.add(text.asText());
            }
            var recTexts = node.get("rec_texts");
            if (recTexts != null && recTexts.isArray()) {
                for (var item : recTexts) {
                    if (!item.asText("").isBlank()) {
                        lines.add(item.asText());
                    }
                }
            }
            node.fields().forEachRemaining(entry -> collect(entry.getValue(), lines, depth + 1));
        } else if (node.isArray()) {
            for (var item : node) {
                collect(item, lines, depth + 1);
            }
        }
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
