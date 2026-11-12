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
 * PaddleOCR HTTP 客户端，按 PaddleOCR 官方在线 API 协议调用。
 *
 * <p>协议来源：PaddleOCR 官网（aistudio.baidu.com/paddleocr）与 AI Studio 帮助文档
 * <a href="https://ai.baidu.com/ai-doc/AISTUDIO/Kmfl2ycs0">PP-OCRv5 API</a>：</p>
 * <pre>
 * POST {endpoint}            // 如 https://xxxx.aistudio-hub.baidu.com/ocr
 * Authorization: token &lt;AI Studio Access Token&gt;
 * {"file": "&lt;base64&gt;", "fileType": 1, "visualize": false}
 * 200 {"errorCode":0,"result":{"ocrResults":[{"prunedResult":{"rec_texts":[...]}}]}}
 * </pre>
 *
 * <p>每次调用只接受一个文件，多页由本类逐页串行调用后拼接。
 * 仍保留对 PaddleX 本地服务（result.ocrResults）与 hubserving（results[].data[].text）
 * 两种返回结构的解析，便于切换部署方式。</p>
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

    /** 识别多张图片，返回按顺序拼接的文本。官方 API 单文件入参，故逐页调用。 */
    public String recognize(List<byte[]> images) {
        if (images.isEmpty()) {
            return "";
        }
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < images.size(); i++) {
            String pageText = recognizeOne(images.get(i), i + 1);
            if (!pageText.isBlank()) {
                if (all.length() > 0) {
                    all.append('\n');
                }
                all.append(pageText);
            }
        }
        return all.toString();
    }

    /** 单张图片走一次官方 API。 */
    private String recognizeOne(byte[] image, int pageNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file", Base64.getEncoder().encodeToString(image));
        body.put("fileType", 1);
        body.put("useDocOrientationClassify", false);
        body.put("useDocUnwarping", false);
        body.put("useTextlineOrientation", false);
        // 不要回传可视化结果图，避免每页返回数 MB 的 base64 图片
        body.put("visualize", false);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                // 官方协议：Authorization: token <AI Studio Access Token>
                builder.header("Authorization", "token " + properties.getApiKey());
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, "PaddleOCR 返回 " + response.statusCode() + "：" + snippet(response.body())
                        + "（第 " + pageNo + " 页）");
            }
            String text = parse(mapper.readTree(response.body()));
            log.info("PaddleOCR 第 {} 页识别 {} 字", pageNo, text.length());
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
