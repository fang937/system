package com.hbsd.labselect.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的网关实现（OpenAI / DeepSeek / 通义千问 / 智谱等均可）。
 *
 * <p>接入方式：把 AI_API_KEY 填上即可，无需改代码。可选 AI_BASE_URL 与 AI_MODEL。
 * 四个能力都是「给定资料 + 固定 JSON 结构」的一次性调用，不做自建 Agent 编排。</p>
 */
@Slf4j
public class OpenAiCompatibleGateway implements AiGateway {

    private static final String SCHEMA_TIPS = """
            {"summary":"一句话结论","focus":[{"code":"评分维度编码","name":"维度名","weight":25,"focus":"本轮考察点"}],
             "questions":[{"question":"问题原文","intent":"想验证什么","outline":["回答要点1","回答要点2"]}],
             "tips":[{"title":"技巧标题","detail":"具体怎么做"}],"selfCheck":["自查项"]}""";

    private static final String SCHEMA_RECOMMEND = """
            {"summary":"排序依据的一句话说明","items":[{"tutorId":1,"tutorName":"","labName":"","title":"",
             "researchDirection":"","matchScore":0,"skillHits":["命中的技能"],"reasons":["推荐理由"],
             "risks":["风险提示"],"suggestedRank":1}]}
            要求：tutorId 必须原样取自输入数据；matchScore 为 0-100 的整数；输出 3-8 条并按 matchScore 降序。""";

    private static final String SCHEMA_REVIEW = """
            {"summary":{"headline":"一句话画像","suggestedTotal":0,"recommendation":"一句话建议"},
             "highlights":["亮点"],"risks":["需核实的风险"],
             "suggestedQuestions":[{"question":"建议提问","intent":"想验证什么","outline":["关注点"]}],
             "dimensions":[{"code":"维度编码","name":"维度名","weight":25,"score":0,"reason":"打分依据","confidence":"LOW|MEDIUM|HIGH"}]}
            要求：dimensions 必须覆盖输入 rubric 中的全部维度，code 与 weight 原样保留，score 为 0-100 的整数；
            suggestedTotal 为按权重折算的总分；confidence 表示该维度仅凭档案推断的把握程度。""";

    private static final String SCHEMA_AVATAR = """
            {"answer":"以导师助手口吻的回答，120 字以内","citations":["引用了哪些资料点"],
             "boundary":"边界声明，如不承诺录取"}""";

    private final AiProperties properties;
    private final AiProperties.Provider provider;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleGateway(AiProperties properties, AiProperties.Provider provider, ObjectMapper mapper) {
        this.properties = properties;
        this.provider = provider;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public String provider() {
        return provider.getName() == null || provider.getName().isBlank()
                ? "openai-compatible" : provider.getName();
    }

    @Override
    public String model() {
        if (provider.getModel() != null && !provider.getModel().isBlank()) {
            return provider.getModel();
        }
        return properties.getModel();
    }

    private String endpoint() {
        String base = provider.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = properties.getBaseUrl();
        }
        if (base == null || base.isBlank()) {
            base = "https://api.openai.com/v1";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    /* ---------------- 四类能力 ---------------- */

    @Override
    public Map<String, Object> interviewTips(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "根据学生的专业、GPA、技能标签与目标导师方向，生成面试准备建议。"
                        + "必须结合学生真实资料，不要编造其没有的经历；问题要具体到可以张口回答。",
                SCHEMA_TIPS, input);
        data.putIfAbsent("summary", "");
        data.putIfAbsent("focus", new ArrayList<>());
        data.putIfAbsent("selfCheck", new ArrayList<>());
        data.put("questions", normalizeQuestions(data.get("questions")));
        data.put("tips", normalizeTips(data.get("tips")));
        return data;
    }

    @Override
    public Map<String, Object> recommend(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "依据技能匹配度、剩余名额、报考竞争度与 GPA 竞争力，对学生可选的导师做排序推荐。"
                        + "必须只使用输入中出现的导师，不得新增；理由要能追溯到资料。",
                SCHEMA_RECOMMEND, input);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : asObjects(data.get("items"))) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("matchScore", intOf(row.get("matchScore")));
            row.putIfAbsent("skillHits", new ArrayList<>());
            row.putIfAbsent("reasons", new ArrayList<>());
            row.putIfAbsent("risks", new ArrayList<>());
            items.add(row);
        }
        items.sort((a, b) -> intOf(b.get("matchScore")) - intOf(a.get("matchScore")));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).put("suggestedRank", i + 1);
        }
        data.put("items", items);
        data.putIfAbsent("summary", "");
        return data;
    }

    @Override
    public Map<String, Object> resumeReview(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "对学生简历与档案做初评，并给出多维评分建议。评分必须贴合评分标准的行为锚点。"
                        + "resume.text 是简历正文（来自 PDF 文本层或 PaddleOCR，可能有识别噪声）："
                        + "有正文时必须引用其中的项目、技能、成果作为依据，并在 reason 里指出出处；"
                        + "正文为空时只能按档案推断，不得虚构简历内容，相关维度要下调 confidence。",
                SCHEMA_REVIEW, input);
        data.putIfAbsent("summary", new LinkedHashMap<>());
        data.putIfAbsent("highlights", new ArrayList<>());
        data.putIfAbsent("risks", new ArrayList<>());
        data.put("suggestedQuestions", normalizeQuestions(data.get("suggestedQuestions")));
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (Map<String, Object> item : asObjects(data.get("dimensions"))) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("score", intOf(row.get("score")));
            row.put("weight", intOf(row.get("weight")));
            row.putIfAbsent("confidence", "MEDIUM");
            dimensions.add(row);
        }
        data.put("dimensions", dimensions);
        return data;
    }

    @Override
    public Map<String, Object> avatarChat(Map<String, Object> input) {
        Map<String, Object> persona = asMap(input.get("persona"));
        String tone = String.valueOf(persona.getOrDefault("tone", "严谨、耐心"));
        Map<String, Object> data = complete(
                "你是导师的 AI 分身，用第一人称回答学生的咨询。语气：" + tone + "。"
                        + "只依据 persona.knowledge 与 tutor 资料回答；涉及录取结果、其他导师、面试原题时明确拒绝。"
                        + "学生问题：" + input.get("question"),
                SCHEMA_AVATAR, input);
        data.putIfAbsent("answer", "这个问题我掌握的信息不足，建议在开放日直接询问导师。");
        data.putIfAbsent("citations", new ArrayList<>());
        data.putIfAbsent("boundary", "不承诺录取结果、不评价其他导师、不透露面试题目");
        return data;
    }

    /* ---------------- 调用与解析 ---------------- */

    private Map<String, Object> complete(String task, String schema, Map<String, Object> input) {
        String system = properties.getSystemPrompt()
                + "\n\n【当前任务】" + task
                + "\n【输出要求】只输出一个 JSON 对象，不要 Markdown 代码块，不要任何解释。字段结构：\n" + schema;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("temperature", properties.getTemperature());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "业务数据（JSON）：\n" + writeJson(input))));
        if (properties.isJsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[{}] AI 调用异常：{}", provider(), e.getMessage());
            throw new BizException(502, "AI 服务连接失败：" + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            String detail = snippet(response.body());
            log.error("[{}] AI 返回 {}：{}", provider(), response.statusCode(), detail);
            if (isQuotaError(response.statusCode(), response.body())) {
                throw new AiQuotaException(provider(), detail);
            }
            throw new BizException(502, "AI 服务返回 " + response.statusCode() + "：" + detail);
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            String content = extractContent(root.path("choices").path(0).path("message").path("content"));
            content = stripFence(content);
            if (content.isBlank()) {
                throw new IllegalStateException("模型返回空内容");
            }
            return mapper.readValue(content, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("AI 结果解析失败：{}", e.getMessage());
            throw new BizException(502, "AI 返回格式无法解析，请检查模型是否支持 JSON 模式（app.ai.json-mode）");
        }
    }

    private String extractContent(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            node.forEach(part -> sb.append(part.path("text").asText("")));
            return sb.toString();
        }
        return "";
    }

    private String stripFence(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine > 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    /* ---------------- 结果规整（防止模型抖动把前端打崩） ---------------- */

    private List<Map<String, Object>> normalizeQuestions(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof String text) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("question", text);
                row.put("intent", "");
                row.put("outline", new ArrayList<>());
                result.add(row);
            } else if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                row.putIfAbsent("outline", new ArrayList<>());
                result.add(row);
            }
        }
        return result;
    }

    private List<Map<String, Object>> normalizeTips(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof String text) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", text);
                row.put("detail", "");
                result.add(row);
            } else if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                result.add(row);
            }
        }
        return result;
    }

    private List<Map<String, Object>> asObjects(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                result.add(row);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private int intOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 判定是否为「额度/配额/限流」类错误：这类错误应切换通道，而不是直接失败。 */
    private boolean isQuotaError(int status, String body) {
        if (status == 402 || status == 429) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String text = body.toLowerCase();
        return text.contains("insufficient_quota") || text.contains("quota")
                || text.contains("insufficient balance") || text.contains("arrears")
                || text.contains("billing") || text.contains("rate limit")
                || text.contains("余额") || text.contains("额度") || text.contains("欠费");
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }
}
