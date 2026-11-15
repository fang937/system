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
 * 各类能力都是「给定资料 + 固定 JSON 结构」的一次性调用，不做自建 Agent 编排。</p>
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
            {"answer":"以导师 AI 分身的口吻（第一人称，不要自称助手），120 字以内","citations":["引用了哪些资料点"],
             "boundary":"边界声明，如不承诺录取"}""";

    private static final String SCHEMA_OPTIMIZE = """
            {"summary":{"scoreBefore":0,"scoreAfter":0,"sectionCount":0,"headline":"一句话结论","advice":"最重要的改法"},
             "suggestions":[{"section":"projects","sectionTitle":"项目经历","before":"原文","after":"改写后",
              "reasons":["改动理由"],"issues":["原文问题"],"focus":["concise"],"changed":true,
              "scoreBefore":0,"scoreAfter":0}],
             "keywordMatches":{"matched":["已覆盖关键词"],"missing":["建议补充关键词"]}}
            要求：每个输入 section 最多 1 条建议，section 必须原样返回；before 必须是输入原文（可截取片段），
            after 只能调整措辞、结构与信息顺序；不得新增学生未提供的项目、公司、数字、奖项；
            缺少量化数据时用「（补充：…）」占位，不得编造；changed 表示 after 是否与 before 不同。""";

    private static final String SCHEMA_COACH_Q = """
            {"section":"self-intro|common|role","sectionTitle":"环节名","question":"问题原文",
             "intent":"想验证什么","tips":["回答提示"],"timeLimitSeconds":120}
            要求：只出 1 道题，问题要具体到可以张口回答；不透露任何真实面试原题。""";

    private static final String SCHEMA_COACH_FB = """
            {"score":0,"level":"优秀|良好|合格|待提升",
             "dimensions":[{"code":"STRUCTURE","name":"结构与逻辑","score":0,"comment":"打分依据"},
              {"code":"CONTENT","name":"内容充实度","score":0,"comment":"打分依据"},
              {"code":"EXPRESSION","name":"表达清晰度","score":0,"comment":"打分依据"},
              {"code":"MATCH","name":"岗位匹配度","score":0,"comment":"打分依据"}],
             "strengths":["做得好的地方"],"improvements":["可改进的地方"],
             "sampleAnswer":"参考表达（结构模板，含占位提示）","followUp":"一道追问题"}
            要求：评分只依据学生本次回答，不得虚构学生未说的经历；score 与各维度 score 均为 0-100 的整数；
            sampleAnswer 只能给结构模板与「……」占位，不得编造具体项目、公司或数字。""";

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

    /**
     * 预热连接：对 {base}/models 发一次短超时请求，把 DNS 解析与 TCP/TLS 握手提前做掉。
     *
     * <p>只关心连接是否建立，不看响应码（401/403 同样说明链路已通），
     * 因此不会消耗 token，也不会因为 Key 权限不足而影响主流程。</p>
     */
    @Override
    public void warmUp() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/models"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .GET()
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("[{}] AI 通道预热完成，用时 {}ms", provider(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.debug("[{}] AI 通道预热未完成（忽略，不影响功能）：{}", provider(), e.getMessage());
        }
    }

    @Override
    public String model() {
        if (provider.getModel() != null && !provider.getModel().isBlank()) {
            return provider.getModel();
        }
        return properties.getModel();
    }

    private String endpoint() {
        return base() + "/chat/completions";
    }

    /** OpenAI 兼容协议的接口根地址（不带 /chat/completions） */
    private String base() {
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
        return base;
    }

    /* ---------------- 管理端调试 ---------------- */

    /** 拉取 Key 下可用模型列表：GET {base}/models，解析 data[].id */
    public List<String> listModels() {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/models"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException(502, "连接模型服务失败：" + e.getMessage());
        }
        if (response.statusCode() / 100 != 2) {
            throw new BizException(502, "模型列表获取失败（HTTP " + response.statusCode() + "）：" + snippet(response.body()));
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            List<String> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    if (id.isBlank()) {
                        id = item.path("name").asText("");
                    }
                    if (!id.isBlank()) {
                        models.add(id);
                    }
                }
            }
            if (models.isEmpty()) {
                throw new BizException(502, "模型列表为空，请确认该 API Key 的权限");
            }
            return models;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "模型列表无法解析：" + e.getMessage());
        }
    }

    /**
     * 调试调用：发一次最小对话验证 Key / 模型 / 连通性。
     * 成功返回 {ok:true, model, latencyMs, reply}；失败返回 {ok:false, status, error}（不抛异常，便于前端内联展示）。
     */
    public Map<String, Object> testCall() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", List.of(Map.of("role", "user", "content", "请只回复两个字：正常")));
        // 预算给足：推理型模型会先消耗 token 生成思考过程，8 个 token 会出现正文为空的假失败
        body.put("max_tokens", 256);
        body.put("temperature", 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider());
        result.put("model", model());
        long start = System.currentTimeMillis();
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
            result.put("ok", false);
            result.put("error", "连接失败：" + e.getMessage());
            return result;
        }
        result.put("latencyMs", System.currentTimeMillis() - start);

        if (response.statusCode() / 100 != 2) {
            result.put("ok", false);
            result.put("status", response.statusCode());
            result.put("error", "HTTP " + response.statusCode() + "：" + snippet(response.body()));
            return result;
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            String content = stripFence(extractContent(root.path("choices").path(0).path("message").path("content")));
            String actualModel = root.path("model").asText(model());
            result.put("ok", content.isBlank() ? false : true);
            result.put("model", actualModel);
            result.put("reply", content);
            if (content.isBlank()) {
                result.put("error", "模型返回空内容");
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "响应无法解析：" + e.getMessage());
        }
        return result;
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
        Map<String, Object> tutor = asMap(input.get("tutor"));
        String tutorName = String.valueOf(tutor.getOrDefault("tutorName", "导师"));
        data.putIfAbsent("answer", "这个问题我掌握的信息还不够，建议在开放日当面问" + tutorName + "老师本人，由老师亲自解答会更准确。");
        data.putIfAbsent("citations", new ArrayList<>());
        data.putIfAbsent("boundary", "不承诺录取结果、不评价其他导师、不透露面试题目");
        return data;
    }

    /* ---------------- 学生端：AI 优化简历 / AI 面试辅导 ---------------- */

    @Override
    public Map<String, Object> resumeOptimize(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "你是校招简历顾问。逐栏改写学生简历：只调整措辞、结构与信息顺序，不改变事实，"
                        + "不新增未提供的项目、公司、数字、奖项。重点覆盖：措辞精炼、成果量化、"
                        + "目标岗位关键词自然融入、专业性与可读性。targetRole 与 keywords 是目标，"
                        + "不得为了匹配关键词编造经历；没有出现的关键词写入 keywordMatches.missing。"
                        + "sections 是学生已保存的简历栏目原文（来自结构化草稿）。",
                SCHEMA_OPTIMIZE, input);

        Map<String, Object> summary = new LinkedHashMap<>();
        if (data.get("summary") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> summary.put(String.valueOf(k), v));
        }
        data.put("summary", summary);

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> item : asObjects(data.get("suggestions"))) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            String before = stringOf(row.get("before"));
            String after = stringOf(row.get("after"));
            if (after.isBlank()) {
                after = before;
            }
            row.put("before", before);
            row.put("after", after);
            row.putIfAbsent("sectionTitle", stringOf(row.get("section")));
            row.put("reasons", asStrings(row.get("reasons")));
            row.put("issues", asStrings(row.get("issues")));
            row.put("focus", asStrings(row.get("focus")));
            row.put("changed", !before.equals(after));
            row.put("scoreBefore", clamp(intOf(row.get("scoreBefore"))));
            row.put("scoreAfter", clamp(intOf(row.get("scoreAfter"))));
            suggestions.add(row);
        }
        data.put("suggestions", suggestions);
        if (!(data.get("keywordMatches") instanceof Map<?, ?> matches)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("matched", new ArrayList<>());
            empty.put("missing", new ArrayList<>());
            data.put("keywordMatches", empty);
        }
        data.putIfAbsent("disclaimer", "AI 只调整措辞与结构，不会新增你的经历；带「（补充：…）」的占位内容需要你填写真实数据后再保存。");
        return data;
    }

    @Override
    public Map<String, Object> interviewCoachQuestion(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "你是校招面试教练。根据面试环节出 1 道题：self-intro 自我介绍、common 常见行为问题、"
                        + "role 岗位/方向相关问题。role 与 tutor 是目标岗位信息；问题要具体、可张口回答，"
                        + "不透露任何真实面试原题，不承诺录取结果。",
                SCHEMA_COACH_Q, input);

        data.putIfAbsent("section", stringOf(input.get("section")));
        data.putIfAbsent("sectionTitle", "面试练习");
        data.putIfAbsent("question", "请先做一个简短的自我介绍。");
        data.putIfAbsent("intent", "考察表达结构与自我定位");
        data.put("tips", asStrings(data.get("tips")));
        data.put("timeLimitSeconds", intOf(data.get("timeLimitSeconds")) > 0
                ? intOf(data.get("timeLimitSeconds")) : 120);
        return data;
    }

    @Override
    public Map<String, Object> interviewCoachFeedback(Map<String, Object> input) {
        Map<String, Object> data = complete(
                "你是校招面试教练。针对学生的一次回答给出反馈：评分必须基于回答文本，"
                        + "不得虚构学生未说的内容；sampleAnswer 只给结构模板与「……」占位，"
                        + "不编造具体项目、公司或数字。dimensions 固定 4 项：STRUCTURE 结构与逻辑、"
                        + "CONTENT 内容充实度、EXPRESSION 表达清晰度、MATCH 岗位匹配度。",
                SCHEMA_COACH_FB, input);

        if (data.get("score") == null) {
            throw new BizException(502, "AI 未返回评分结果，请重试");
        }
        int score = clamp(intOf(data.get("score")));
        data.put("score", score);
        data.putIfAbsent("level", score >= 85 ? "优秀" : score >= 75 ? "良好" : score >= 60 ? "合格" : "待提升");

        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (Map<String, Object> item : asObjects(data.get("dimensions"))) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("score", clamp(intOf(row.get("score"))));
            row.putIfAbsent("name", stringOf(row.get("code")));
            row.putIfAbsent("comment", "");
            dimensions.add(row);
        }
        data.put("dimensions", dimensions);
        data.put("strengths", asStrings(data.get("strengths")));
        data.put("improvements", asStrings(data.get("improvements")));
        data.putIfAbsent("sampleAnswer", "请按「背景 - 任务 - 行动 - 结果」组织，并替换为你自己的真实经历。");
        data.putIfAbsent("followUp", "你能把刚才提到的项目再具体讲一层吗？");
        data.putIfAbsent("disclaimer", "反馈只针对这一次表达，不代表导师评价，也不预测录取结果。");
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

    private List<String> asStrings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
            }
        }
        return result;
    }

    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

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
