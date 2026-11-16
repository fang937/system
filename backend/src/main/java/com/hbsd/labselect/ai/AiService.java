package com.hbsd.labselect.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.dto.InterviewCoachFeedbackReq;
import com.hbsd.labselect.dto.InterviewCoachQuestionReq;
import com.hbsd.labselect.dto.ResumeOptimizeReq;
import com.hbsd.labselect.ocr.ResumeTextService;
import com.hbsd.labselect.service.PhaseService;
import com.hbsd.labselect.service.NotifyService;
import com.hbsd.labselect.service.ResumeDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 能力编排层：负责取业务数据、调用 {@link AiGateway}、留痕并统一包装返回。
 * 业务规则（谁能看、谁的数据）都在这里，模型供应商只关心输入输出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final JdbcTemplate jdbc;
    private final AiGateway gateway;
    private final ObjectMapper objectMapper;
    private final PhaseService phaseService;
    private final ResumeTextService resumeTextService;
    private final ResumeTagService resumeTagService;
    private final ResumeProfileService resumeProfileService;
    private final ResumeDraftService resumeDraftService;
    private final NotifyService notifyService;

    /* ---------------- 评分标准 ---------------- */

    /** 评分标准：维度、权重、行为锚点。前端打分面板与 AI 建议共用同一份数据。 */
    public List<Map<String, Object>> rubric() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, name, weight, description, anchors, sort_no AS sortNo
                  FROM score_rubrics WHERE status = 1 ORDER BY sort_no
                """);
        for (Map<String, Object> row : rows) {
            Object anchors = row.get("anchors");
            try {
                row.put("anchors", anchors == null ? List.of()
                        : objectMapper.readValue(String.valueOf(anchors), new TypeReference<List<String>>() {
                        }));
            } catch (Exception e) {
                row.put("anchors", List.of());
            }
        }
        return rows;
    }

    public Map<String, Integer> weights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Map<String, Object> row : rubric()) {
            weights.put(String.valueOf(row.get("code")), ((Number) row.get("weight")).intValue());
        }
        return weights;
    }

    /* ---------------- 通道管理 ---------------- */

    public List<Map<String, Object>> providers() {
        return gateway.providers();
    }

    public void switchProvider(String name) {
        gateway.switchProvider(name);
    }

    /* ---- 管理端调试：模型列表 / 连通性测试 / 模型切换 ---- */

    /** system_config 中模型覆盖的键前缀，如 ai.model.deepseek */
    public static final String MODEL_KEY_PREFIX = "ai.model.";

    /** system_config 中 API Key 的键前缀，如 ai.key.deepseek（页面写入优先于 .env） */
    public static final String API_KEY_PREFIX = "ai.key.";

    /** 拉取某通道 API Key 下的可用模型列表 */
    public List<String> providerModels(String name) {
        return gateway.listModels(name);
    }

    /** 调试某通道：一次最小对话，验证 Key 与连通性 */
    public Map<String, Object> testProvider(String name) {
        return gateway.testCall(name);
    }

    /** 切换某通道使用的模型：运行时立即生效，并持久化到 system_config（重启后保留） */
    public void selectModel(String name, String model) {
        gateway.setModel(name, model);
        saveConfig(MODEL_KEY_PREFIX + normalizeName(name), model.trim(), "AI 通道模型选择（管理端可切换）");
        log.info("AI 通道 {} 模型已保存为 {}", name, model.trim());
    }

    /** 写入某通道的 API Key：运行时立即生效，并持久化到 system_config（优先于 .env） */
    public void selectApiKey(String name, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException("API Key 不能为空");
        }
        gateway.setApiKey(name, apiKey.trim());
        saveConfig(API_KEY_PREFIX + normalizeName(name), apiKey.trim(), "AI 通道 API Key（管理端写入，优先于 .env）");
        log.info("AI 通道 {} 的 API Key 已保存", name);
    }

    /** 启动时恢复管理端保存过的模型选择与 API Key（没保存过的通道沿用 .env 配置） */
    @jakarta.annotation.PostConstruct
    void restoreOverrides() {
        restoreConfig(MODEL_KEY_PREFIX, (name, value) -> gateway.setModel(name, value), "模型");
        restoreConfig(API_KEY_PREFIX, (name, value) -> gateway.setApiKey(name, value), "API Key");
    }

    /**
     * 启动完成后异步预热模型通道。
     *
     * <p>必须放在 ApplicationReadyEvent 而不是 @PostConstruct：后者早于
     * {@link #restoreOverrides()} 之外的数据库写入顺序保证，此时 Key 可能还没从
     * system_config 里恢复出来。异步 + 守护线程，预热失败或变慢都不拖启动。</p>
     */
    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    void warmUpGateway() {
        Thread worker = new Thread(() -> {
            try {
                gateway.warmUp();
            } catch (Exception e) {
                log.debug("AI 通道预热失败（忽略）：{}", e.getMessage());
            }
        }, "ai-warmup");
        worker.setDaemon(true);
        worker.start();
    }

    private void restoreConfig(String prefix, java.util.function.BiConsumer<String, String> apply, String label) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM system_config WHERE config_key LIKE ?", prefix + "%");
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.get("config_key"));
                String value = row.get("config_value") == null ? "" : String.valueOf(row.get("config_value"));
                if (value.isBlank()) {
                    continue;
                }
                String name = key.substring(prefix.length());
                try {
                    apply.accept(name, value);
                } catch (Exception e) {
                    log.warn("恢复 AI 通道 {} 的{}失败：{}", name, label, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("读取 {} 覆盖配置失败（忽略，沿用默认）：{}", prefix, e.getMessage());
        }
    }

    private void saveConfig(String key, String value, String description) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, key);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE system_config SET config_value = ?, description = ? WHERE config_key = ?",
                    value, description, key);
        } else {
            jdbc.update("""
                    INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                    VALUES (?, ?, 'STRING', 'ai', ?)
                    """, key, value, description);
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    /* ---------------- 学生端 ---------------- */

    public Map<String, Object> interviewTips() {
        Long studentId = UserContext.get().id();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("tutors", targetTutors(studentId));
        input.put("rubric", rubric());
        Map<String, Object> data = gateway.interviewTips(input);
        return envelope("INTERVIEW_TIPS", data, Map.of("studentId", studentId), studentId, null, null);
    }

    public Map<String, Object> recommendations() {
        Long studentId = UserContext.get().id();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("tutors", allTutors());
        input.put("rubric", rubric());
        Map<String, Object> data = gateway.recommend(input);
        return envelope("RECOMMEND", data, Map.of("studentId", studentId), studentId, null, null);
    }

    /** 学生考察期可咨询的导师分身列表。 */
    public List<Map<String, Object>> avatars() {
        List<Map<String, Object>> tutors = allTutors();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> tutor : tutors) {
            Long tutorId = ((Number) tutor.get("tutorId")).longValue();
            Map<String, Object> persona = personaOf(tutorId, tutor);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tutorId", tutorId);
            row.put("tutorName", tutor.get("tutorName"));
            row.put("title", tutor.get("title"));
            row.put("labName", tutor.get("labName"));
            row.put("researchDirection", tutor.get("researchDirection"));
            row.put("displayName", persona.get("displayName"));
            row.put("greeting", persona.get("greeting"));
            row.put("expertise", persona.get("expertise"));
            row.put("boundaries", persona.get("boundaries"));
            row.put("source", persona.get("source"));
            row.put("status", persona.get("status"));
            result.add(row);
        }
        return result;
    }

    public Map<String, Object> chat(Long tutorId, String question, List<Map<String, String>> history) {
        if (question == null || question.isBlank()) {
            throw new BizException("请先输入想问的问题");
        }
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        Map<String, Object> persona = personaOf(tutorId, tutor);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("persona", persona);
        input.put("tutor", tutor);
        input.put("question", question.trim());
        input.put("history", history == null ? List.of() : history);

        Long studentId = UserContext.get().id();
        // 学生问题先落库：即使模型调用失败，导师也能在工作台看到并真人回复
        Long studentMessageId = insertAvatarMessage(tutorId, studentId, "STUDENT", question.trim(), null);

        Map<String, Object> data = gateway.avatarChat(input);
        String answer = data.get("answer") == null ? "" : String.valueOf(data.get("answer")).trim();
        Long aiMessageId = null;
        if (!answer.isEmpty()) {
            aiMessageId = insertAvatarMessage(tutorId, studentId, "AI", answer,
                    citationsJson(data.get("citations")));
        }
        data.put("studentMessageId", studentMessageId);
        data.put("aiMessageId", aiMessageId);
        return envelope("AVATAR_CHAT", data,
                Map.of("tutorId", tutorId, "question", question), studentId, tutorId, null);
    }

    /* ---------------- 学生与导师分身 / 真人的会话 ---------------- */

    /** 学生端：自己与某位导师分身（含真人回复）的完整会话。 */
    public List<Map<String, Object>> avatarMessages(Long tutorId) {
        Long studentId = UserContext.get().id();
        if (tutorById(tutorId) == null) {
            throw new BizException("导师不存在");
        }
        return avatarThread(tutorId, studentId);
    }

    /** 导师端：有哪些学生找过我的分身，带最后一条消息与未读数。 */
    public List<Map<String, Object>> myAvatarConversations() {
        Long tutorId = currentTutorId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.student_id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.class_name AS className, MAX(m.created_at) AS lastAt,
                       (SELECT x.content FROM avatar_messages x
                         WHERE x.tutor_id = m.tutor_id AND x.student_id = m.student_id
                         ORDER BY x.id DESC LIMIT 1) AS lastContent,
                       (SELECT x.sender_type FROM avatar_messages x
                         WHERE x.tutor_id = m.tutor_id AND x.student_id = m.student_id
                         ORDER BY x.id DESC LIMIT 1) AS lastSender,
                       SUM(CASE WHEN m.sender_type = 'STUDENT' AND m.teacher_read = 0 THEN 1 ELSE 0 END) AS unreadCount
                  FROM avatar_messages m
                  JOIN users u ON u.id = m.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = m.student_id
                 WHERE m.tutor_id = ?
                 GROUP BY m.tutor_id, m.student_id, u.name, u.student_no, sp.class_name
                 ORDER BY lastAt DESC
                """, tutorId);
        for (Map<String, Object> row : rows) {
            row.put("lastSenderLabel", senderLabel(String.valueOf(row.get("lastSender"))));
        }
        return rows;
    }

    /** 导师端：与某位学生的完整会话；打开即把学生消息标记为已读。 */
    public Map<String, Object> myAvatarConversation(Long studentId) {
        Long tutorId = currentTutorId();
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT u.id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.class_name AS className
                  FROM users u LEFT JOIN student_profiles sp ON sp.user_id = u.id
                 WHERE u.id = ? AND u.role = 'STUDENT'
                """, studentId);
        if (students.isEmpty()) {
            throw new BizException("学生不存在");
        }
        jdbc.update("""
                UPDATE avatar_messages SET teacher_read = 1
                 WHERE tutor_id = ? AND student_id = ? AND sender_type = 'STUDENT' AND teacher_read = 0
                """, tutorId, studentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("student", students.get(0));
        result.put("messages", avatarThread(tutorId, studentId));
        return result;
    }

    /** 导师端：以真人身份回复。消息与学生、AI 回答在同一线程，前端标注「真人老师」。 */
    public Map<String, Object> replyToStudent(Long studentId, String content) {
        Long tutorId = currentTutorId();
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException("回复内容不能为空");
        }
        if (text.length() > 1000) {
            throw new BizException("回复最长 1000 字");
        }
        Integer existed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM avatar_messages WHERE tutor_id = ? AND student_id = ?
                """, Integer.class, tutorId, studentId);
        if (existed == null || existed == 0) {
            throw new BizException("该学生还没有向你的分身提问，无法回复");
        }
        Long messageId = insertAvatarMessage(tutorId, studentId, "TEACHER", text, null);
        jdbc.update("""
                UPDATE avatar_messages SET teacher_read = 1
                 WHERE tutor_id = ? AND student_id = ? AND sender_type = 'STUDENT' AND teacher_read = 0
                """, tutorId, studentId);

        Map<String, Object> tutor = tutorById(tutorId);
        String tutorName = tutor == null ? "导师" : String.valueOf(tutor.get("tutorName"));
        notifyService.send(studentId, "NOTICE", "导师回复了你的提问",
                tutorName + "老师以真人身份回复了你在 AI 分身对话中的提问，打开「导师与方向 → AI 分身」即可查看。",
                "AVATAR", tutorId);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("senderType", "TEACHER");
        message.put("senderLabel", senderLabel("TEACHER"));
        message.put("content", text);
        message.put("citations", List.of());
        message.put("createdAt", LocalDateTime.now().withNano(0).toString());
        message.put("mine", true);
        return message;
    }

    private List<Map<String, Object>> avatarThread(Long tutorId, Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, sender_type AS senderType, content, citations, created_at AS createdAt
                  FROM avatar_messages
                 WHERE tutor_id = ? AND student_id = ?
                 ORDER BY id
                 LIMIT 200
                """, tutorId, studentId);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String senderType = String.valueOf(row.get("senderType"));
            item.put("senderLabel", senderLabel(senderType));
            item.put("citations", parseCitations(row.get("citations")));
            item.put("mine", "TEACHER".equals(senderType) && isTeacherView()
                    || "STUDENT".equals(senderType) && !isTeacherView());
            messages.add(item);
        }
        return messages;
    }

    /** 当前请求是导师端还是学生端：仅用于给消息加「我」标记，不参与权限判断。 */
    private boolean isTeacherView() {
        return List.of("TUTOR", "LAB_ADMIN").contains(UserContext.get().role());
    }

    private Long insertAvatarMessage(Long tutorId, Long studentId, String senderType,
                                     String content, String citations) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO avatar_messages (tutor_id, student_id, sender_type, content, citations, teacher_read)
                    VALUES (?, ?, ?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tutorId);
            ps.setLong(2, studentId);
            ps.setString(3, senderType);
            ps.setString(4, content);
            if (citations == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, citations);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String citationsJson(Object citations) {
        if (citations == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseCitations(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String senderLabel(String senderType) {
        return switch (senderType) {
            case "AI" -> "AI 分身";
            case "TEACHER" -> "真人老师";
            default -> "学生";
        };
    }

    /* ---------------- 学生端：AI 优化简历 ---------------- */

    /**
     * 逐栏优化简历。正文一律以服务端草稿为准，前端在调用前会先保存，
     * 避免“请求体里的内容”和“已保存草稿”出现两个版本。
     *
     * <p>前置条件：必须先上传 PDF / DOCX 简历。未上传时不允许调用 AI 分析，
     * 只允许学生手动编辑结构化草稿。</p>
     */
    public Map<String, Object> resumeOptimize(ResumeOptimizeReq req) {
        Long studentId = UserContext.get().id();
        Integer resumeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_resumes WHERE student_id = ?", Integer.class, studentId);
        if (resumeCount == null || resumeCount == 0) {
            throw new BizException(400, "尚未上传简历，请先上传 PDF / DOCX 简历后再使用 AI 分析");
        }
        Map<String, Object> draft = resumeDraftService.load(studentId);
        List<Map<String, Object>> sections = resumeDraftService.pickSections(draft, req == null ? null : req.sections());
        if (sections.isEmpty()) {
            throw new BizException(400, "所选栏目内容为空，请先填写简历内容（至少 30 字）后再优化");
        }
        int total = sections.stream()
                .mapToInt(section -> String.valueOf(section.get("content")).length())
                .sum();
        if (total < ResumeDraftService.MIN_OPTIMIZE_CHARS) {
            throw new BizException(400, "所选栏目内容过短（共 " + total + " 字），至少填写 "
                    + ResumeDraftService.MIN_OPTIMIZE_CHARS + " 字后再优化");
        }
        if (total > ResumeDraftService.MAX_TOTAL_CHARS) {
            throw new BizException(400, "简历内容超过 " + ResumeDraftService.MAX_TOTAL_CHARS + " 字，请精简后再优化");
        }

        String targetRole = firstNonBlank(
                req == null ? null : req.targetRole(),
                resumeDraftService.targetRoleOf(draft),
                defaultTargetRole(studentId));
        List<String> focus = normalizeFocus(req == null ? null : req.focus(),
                resumeDraftService.focusOf(draft));
        List<String> keywords = normalizeKeywords(req == null ? null : req.keywords(),
                resumeDraftService.keywordsOf(draft), defaultKeywords(studentId));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("targetRole", targetRole);
        input.put("keywords", keywords);
        input.put("focus", focus);
        input.put("sections", sections);

        Map<String, Object> data = gateway.resumeOptimize(input);
        data.putIfAbsent("targetRole", targetRole);
        data.putIfAbsent("focus", focus);
        data.put("draftUpdatedAt", draft.get("updatedAt"));
        return envelope("RESUME_OPTIMIZE", data,
                Map.of("sections", sections.stream().map(s -> s.get("key")).toList(),
                        "focus", focus, "targetRole", targetRole),
                studentId, null, null);
    }

    /* ---------------- 学生端：AI 面试辅导 ---------------- */

    /** 生成一道练习题；target 导师与方向会带进提示词，用于岗位相关问题模拟。 */
    public Map<String, Object> interviewCoachQuestion(InterviewCoachQuestionReq req) {
        Long studentId = UserContext.get().id();
        String section = normalizeSection(req == null ? null : req.section());
        Map<String, Object> tutor = resolveCoachTutor(req == null ? null : req.tutorId());
        String role = firstNonBlank(req == null ? null : req.role(),
                tutor == null ? null : str(tutor.get("researchDirection")),
                defaultTargetRole(studentId),
                "目标方向");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("section", section);
        input.put("tutor", tutor);
        input.put("role", role);
        input.put("variant", req == null || req.variant() == null ? 0 : Math.max(0, req.variant()));
        input.put("rubric", rubric());

        Map<String, Object> data = gateway.interviewCoachQuestion(input);
        data.putIfAbsent("section", section);
        data.putIfAbsent("sectionTitle", coachSectionTitle(section));
        data.putIfAbsent("timeLimitSeconds", 120);
        data.put("targetLabel", firstNonBlank(
                tutor == null ? null : str(tutor.get("labName")),
                role));
        return envelope("INTERVIEW_COACH_Q", data,
                Map.of("section", section, "tutorId", tutor == null ? "" : tutor.get("tutorId")),
                studentId, tutor == null ? null : asLong(tutor.get("tutorId")), null);
    }

    /** 提交作答并返回反馈；每次反馈落一条练习记录，便于回看进步。 */
    public Map<String, Object> interviewCoachFeedback(InterviewCoachFeedbackReq req) {
        Long studentId = UserContext.get().id();
        String section = normalizeSection(req.section());
        String question = req.question() == null ? "" : req.question().trim();
        String answer = req.answer() == null ? "" : req.answer().trim();
        if (question.isBlank()) {
            throw new BizException(400, "缺少题目，请重新出题");
        }
        if (answer.length() < 10) {
            throw new BizException(400, "回答太短（" + answer.length() + " 字），至少写 10 个字再提交");
        }
        if (answer.length() > 2000) {
            throw new BizException(400, "回答超过 2000 字，请精简后再提交");
        }

        Map<String, Object> tutor = resolveCoachTutor(req.tutorId());
        String role = firstNonBlank(req.role(),
                tutor == null ? null : str(tutor.get("researchDirection")),
                defaultTargetRole(studentId),
                "目标方向");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("section", section);
        input.put("tutor", tutor);
        input.put("role", role);
        input.put("question", question);
        input.put("questionIntent", req.questionIntent());
        input.put("answer", answer);
        input.put("rubric", rubric());

        Map<String, Object> data = gateway.interviewCoachFeedback(input);
        data.putIfAbsent("section", section);
        data.putIfAbsent("sectionTitle", coachSectionTitle(section));
        data.put("question", question);
        data.put("answer", answer);
        data.put("targetLabel", firstNonBlank(
                tutor == null ? null : str(tutor.get("labName")),
                role));
        data.put("createdAt", LocalDateTime.now().withNano(0).toString());

        Long recordId = saveCoachRecord(studentId, section, tutor, role, question, answer, data);
        data.put("recordId", recordId);

        return envelope("INTERVIEW_COACH_FB", data,
                Map.of("section", section, "question", question, "answerChars", answer.length()),
                studentId, tutor == null ? null : asLong(tutor.get("tutorId")), null);
    }

    /** 最近 10 次练习记录（本人可见）。 */
    public List<Map<String, Object>> interviewCoachHistory() {
        Long studentId = UserContext.get().id();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.section, r.tutor_id AS tutorId,
                       u.name AS tutorName, r.target_role AS targetRole,
                       r.question, r.answer, r.score, r.feedback, r.created_at AS createdAt
                  FROM interview_coach_records r
                  LEFT JOIN tutors t ON t.id = r.tutor_id
                  LEFT JOIN users u ON u.id = t.user_id
                 WHERE r.student_id = ?
                 ORDER BY r.created_at DESC, r.id DESC
                 LIMIT 10
                """, studentId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("sectionTitle", coachSectionTitle(String.valueOf(row.get("section"))));
            item.put("feedback", parseFeedback(row.get("feedback")));
            result.add(item);
        }
        return result;
    }

    /* ---------------- 教师端 ---------------- */

    /**
     * 导师端：查看「自己的分身」接入状态与身份设定。
     *
     * <p>学生端看到的分身由本接口的数据驱动，导师据此确认模型是否真的接通、
     * 当前用的是哪个通道与模型、身份设定是自己的还是系统按资料推导的。</p>
     */
    public Map<String, Object> myAvatar() {
        Long tutorId = currentTutorId();
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException(403, "当前账号没有对应的导师资料");
        }
        Map<String, Object> persona = personaOf(tutorId, tutor);
        boolean placeholder = "mock".equals(gateway.provider());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tutorId", tutorId);
        result.put("tutorName", tutor.get("tutorName"));
        result.put("labName", tutor.get("labName"));
        result.put("researchDirection", tutor.get("researchDirection"));
        result.put("persona", persona);
        result.put("engine", gateway.provider());
        result.put("provider", gateway.activeName());
        result.put("model", gateway.model());
        result.put("placeholder", placeholder);
        // 「已接入」= 走的是真实模型通道，而不是内置规则引擎
        result.put("connected", !placeholder);
        return result;
    }

    /** 导师端：保存自己的分身设定。只能改自己，导师 id 由登录态推导，不接受前端传入 */
    public void saveMyPersona(Map<String, Object> body) {
        savePersona(currentTutorId(), body);
    }

    /**
     * 导师端：用自己分身做一次试聊，验证「学生问过来会得到什么」。
     * 与学生的真实提问共用同一条网关链路，但学生问题不会进这里。
     */
    public Map<String, Object> testMyAvatarChat(String question) {
        if (question == null || question.isBlank()) {
            throw new BizException("请先输入想问的问题");
        }
        Long tutorId = currentTutorId();
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException(403, "当前账号没有对应的导师资料");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("persona", personaOf(tutorId, tutor));
        input.put("tutor", tutor);
        input.put("question", question.trim());
        input.put("history", List.of());

        Map<String, Object> data = gateway.avatarChat(input);
        return envelope("AVATAR_CHAT_TEST", data, Map.of("question", question.trim()),
                null, tutorId, null);
    }

    public Map<String, Object> resumeReview(Long applicationId) {
        Long tutorId = currentTutorId();
        Map<String, Object> application = jdbc.queryForMap("""
                SELECT a.id, a.round_no AS roundNo, a.tutor_1_id AS tutor1Id,
                       a.tutor_2_id AS tutor2Id, a.tutor_3_id AS tutor3Id,
                       a.student_id AS studentId, a.status
                  FROM applications a WHERE a.id = ?
                """, applicationId);

        boolean mine = tutorId.equals(asLong(application.get("tutor1Id")))
                || tutorId.equals(asLong(application.get("tutor2Id")))
                || tutorId.equals(asLong(application.get("tutor3Id")));
        if (!mine) {
            throw new BizException(403, "该学生未报考你，无法查看简历初评");
        }
        application.put("choiceIndex", choiceIndex(application, tutorId));

        Long studentId = asLong(application.get("studentId"));
        Map<String, Object> resume = resumeMeta(studentId);
        // 前置条件：AI 初评必须基于学生上传的简历文件，未上传直接拒绝（手工打分不受影响）
        if (!Boolean.TRUE.equals(resume.get("uploaded"))) {
            throw new BizException(400, "该学生尚未上传简历，无法进行 AI 初评");
        }
        ResumeTextService.Extract extract = resumeTextService.extract(studentId);
        if (extract.hasText()) {
            resume.put("text", truncate(extract.text(), 4000));
        }
        resume.put("textEngine", extract.engine());
        resume.put("textChars", extract.chars());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("student", studentProfile(studentId));
        input.put("application", application);
        input.put("tutor", tutorById(tutorId));
        input.put("rubric", rubric());
        input.put("resume", resume);

        Map<String, Object> data = gateway.resumeReview(input);

        // 简历技能标签：词表抽取，模型不可用时也能给导师端一个可读的技能视图
        List<Map<String, Object>> resumeTags = resumeTagService.tags(extract.text());
        data.put("resumeTags", resumeTags);
        data.put("resumeTagNames", resumeTags.stream().map(t -> String.valueOf(t.get("name"))).toList());
        // 简历画像：技术栈之外再给经历与性格特质
        data.put("resumeProfile", resumeProfileService.profile(extract.text()));

        data.put("resumeDigest", Map.of(
                "engine", extract.engine(),
                "chars", extract.chars(),
                "pages", extract.pages(),
                "used", extract.hasText(),
                "cached", extract.cached()));
        return envelope("RESUME_REVIEW", data, Map.of("applicationId", applicationId),
                studentId, tutorId, applicationId);
    }

    /* ---------------- 分身身份（管理员维护） ---------------- */

    public Map<String, Object> personaDetail(Long tutorId) {
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        return personaOf(tutorId, tutor);
    }

    public void savePersona(Long tutorId, Map<String, Object> body) {
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        jdbc.update("""
                INSERT INTO ai_personas (tutor_id, display_name, tone, expertise, boundaries, greeting, knowledge, model, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), tone = VALUES(tone),
                    expertise = VALUES(expertise), boundaries = VALUES(boundaries), greeting = VALUES(greeting),
                    knowledge = VALUES(knowledge), model = VALUES(model), status = VALUES(status)
                """,
                tutorId,
                text(body, "displayName", defaultDisplayName(tutor)),
                text(body, "tone", "严谨、耐心，像实验室的师兄师姐"),
                text(body, "expertise", str(tutor.get("researchDirection"))),
                text(body, "boundaries", "不承诺录取结果、不评价其他导师、不透露面试题目"),
                text(body, "greeting", defaultGreeting(tutor)),
                text(body, "knowledge", defaultKnowledge(tutor)),
                text(body, "model", ""),
                body.get("status") == null ? 1 : ((Number) body.get("status")).intValue());
    }

    /* ---------------- 数据读取 ---------------- */

    private List<String> normalizeFocus(List<String> requested, List<String> fallback) {
        List<String> source = requested == null || requested.isEmpty() ? fallback : requested;
        List<String> focus = new ArrayList<>();
        for (String item : source) {
            String key = item == null ? "" : item.trim();
            if (ResumeDraftService.FOCUS_KEYS.contains(key) && !focus.contains(key)) {
                focus.add(key);
            }
        }
        return focus.isEmpty() ? ResumeDraftService.FOCUS_KEYS : focus;
    }

    private List<String> normalizeKeywords(List<String> requested, List<String> draftKeywords,
                                          List<String> defaults) {
        List<String> source = requested != null && !requested.isEmpty()
                ? requested
                : (draftKeywords != null && !draftKeywords.isEmpty() ? draftKeywords : defaults);
        List<String> keywords = new ArrayList<>();
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String word = item.trim().replaceAll("\\s+", " ");
            if (word.isEmpty() || keywords.contains(word)) {
                continue;
            }
            keywords.add(word.length() > 30 ? word.substring(0, 30) : word);
            if (keywords.size() >= 12) {
                break;
            }
        }
        return keywords;
    }

    /** 目标岗位默认取当前第一志愿导师的研究方向；未填报时退回专业。 */
    private String defaultTargetRole(Long studentId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT t.research_direction AS direction, l.name AS labName
                      FROM applications a
                      JOIN tutors t ON t.id = a.tutor_1_id
                      JOIN labs l ON l.id = t.lab_id
                     WHERE a.student_id = ? AND a.round_no = ?
                     LIMIT 1
                    """, studentId, phaseService.currentRound());
            if (!rows.isEmpty()) {
                String direction = str(rows.get(0).get("direction"));
                if (!direction.isBlank()) {
                    return direction;
                }
                String labName = str(rows.get(0).get("labName"));
                if (!labName.isBlank()) {
                    return labName;
                }
            }
        } catch (Exception e) {
            log.debug("读取目标方向失败（忽略）：{}", e.getMessage());
        }
        String major = str(studentProfile(studentId).get("major"));
        return major.isBlank() ? "目标方向" : major + "相关方向";
    }

    /** 学生已填的技能标签作为目标关键词的默认值。 */
    private List<String> defaultKeywords(Long studentId) {
        String tags = str(studentProfile(studentId).get("skillTags"));
        if (tags.isBlank()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String part : tags.split("[,，;；/、|]")) {
            String word = part.trim();
            if (!word.isEmpty() && !keywords.contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private String normalizeSection(String section) {
        String value = section == null ? "" : section.trim();
        if (value.isEmpty()) {
            return "common";
        }
        if (!List.of("self-intro", "common", "role").contains(value)) {
            throw new BizException(400, "面试环节不合法：仅支持自我介绍 / 常见问题 / 岗位相关问题");
        }
        return value;
    }

    private String coachSectionTitle(String section) {
        return switch (section == null ? "" : section) {
            case "self-intro" -> "自我介绍";
            case "role" -> "岗位相关问题模拟";
            default -> "常见问题演练";
        };
    }

    private Map<String, Object> resolveCoachTutor(Long tutorId) {
        if (tutorId == null) {
            return null;
        }
        Map<String, Object> tutor = tutorById(tutorId);
        if (tutor == null) {
            throw new BizException(400, "目标导师不存在，请重新选择");
        }
        return tutor;
    }

    private Long saveCoachRecord(Long studentId, String section, Map<String, Object> tutor, String role,
                                 String question, String answer, Map<String, Object> feedback) {
        try {
            Map<String, Object> record = new LinkedHashMap<>(feedback);
            record.remove("question");
            record.remove("answer");
            record.remove("recordId");
            String json = objectMapper.writeValueAsString(record);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO interview_coach_records
                            (student_id, section, tutor_id, target_role, question, answer, score, feedback)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, studentId);
                ps.setString(2, section);
                Long tutorId = tutor == null ? null : asLong(tutor.get("tutorId"));
                if (tutorId == null) {
                    ps.setNull(3, Types.BIGINT);
                } else {
                    ps.setLong(3, tutorId);
                }
                ps.setString(4, role);
                ps.setString(5, question);
                ps.setString(6, answer);
                Object score = feedback.get("score");
                if (score instanceof Number number) {
                    ps.setInt(7, number.intValue());
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                ps.setString(8, json);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        } catch (Exception e) {
            log.warn("面试辅导记录保存失败（不影响反馈返回）：{}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseFeedback(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private Map<String, Object> studentProfile(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.name, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.grade, sp.gpa,
                       sp.skill_tags AS skillTags, sp.resume_url AS resumeUrl,
                       sp.priority_score AS priorityScore
                  FROM users u LEFT JOIN student_profiles sp ON sp.user_id = u.id
                 WHERE u.id = ?
                """, studentId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private List<Map<String, Object>> allTutors() {
        return jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title,
                       t.research_direction AS researchDirection, t.intro,
                       t.quota, t.remaining_quota AS remainingQuota,
                       l.id AS labId, l.name AS labName, l.description AS labDescription,
                       (SELECT COUNT(*) FROM applications a WHERE a.round_no = ?
                          AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id)) AS applicantCount
                  FROM tutors t
                  JOIN users u ON u.id = t.user_id
                  JOIN labs l  ON l.id = t.lab_id
                 WHERE t.status = 1 AND u.status = 1
                 ORDER BY l.id, t.id
                """, phaseService.currentRound());
    }

    /** 简历是否已上传（只给元数据，不解析文件内容） */
    private Map<String, Object> resumeMeta(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT file_name AS fileName, size_bytes AS sizeBytes, uploaded_at AS uploadedAt
                  FROM student_resumes WHERE student_id = ?
                """, studentId);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("uploaded", !rows.isEmpty());
        if (!rows.isEmpty()) {
            meta.putAll(rows.get(0));
        }
        return meta;
    }

    private Map<String, Object> tutorById(Long tutorId) {        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title,
                       t.research_direction AS researchDirection, t.intro,
                       t.quota, t.remaining_quota AS remainingQuota,
                       l.id AS labId, l.name AS labName, l.description AS labDescription
                  FROM tutors t
                  JOIN users u ON u.id = t.user_id
                  JOIN labs l  ON l.id = t.lab_id
                 WHERE t.id = ?
                """, tutorId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 学生当前志愿命中的导师；未填志愿时退回全部导师，用于面试技巧分析的场景代入。 */
    private List<Map<String, Object>> targetTutors(Long studentId) {
        List<Map<String, Object>> all = allTutors();
        List<Map<String, Object>> apps = jdbc.queryForList("""
                SELECT tutor_1_id AS t1, tutor_2_id AS t2, tutor_3_id AS t3
                  FROM applications WHERE student_id = ? AND round_no = ?
                """, studentId, phaseService.currentRound());
        if (apps.isEmpty()) {
            return all;
        }
        Map<String, Object> app = apps.get(0);
        List<Long> ids = new ArrayList<>();
        for (String key : List.of("t1", "t2", "t3")) {
            Long id = asLong(app.get(key));
            if (id != null) {
                ids.add(id);
            }
        }
        List<Map<String, Object>> picked = all.stream()
                .filter(t -> ids.contains(asLong(t.get("tutorId"))))
                .toList();
        return picked.isEmpty() ? all : picked;
    }

    private Map<String, Object> personaOf(Long tutorId, Map<String, Object> tutor) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT display_name AS displayName, tone, expertise, boundaries, greeting,
                       knowledge, model, status
                  FROM ai_personas WHERE tutor_id = ?
                """, tutorId);
        Map<String, Object> persona = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            persona.putAll(rows.get(0));
            persona.put("source", "custom");
            persona.put("tutorId", tutorId);
            return persona;
        }
        persona.put("tutorId", tutorId);
        persona.put("displayName", defaultDisplayName(tutor));
        persona.put("tone", "严谨、耐心，像实验室的师兄师姐");
        persona.put("expertise", tutor.get("researchDirection"));
        persona.put("boundaries", "不承诺录取结果、不评价其他导师、不透露面试题目");
        persona.put("greeting", defaultGreeting(tutor));
        persona.put("knowledge", defaultKnowledge(tutor));
        persona.put("model", "");
        persona.put("status", 1);
        persona.put("source", "derived");
        return persona;
    }

    private String defaultDisplayName(Map<String, Object> tutor) {
        return str(tutor.get("tutorName")) + "老师的 AI 分身";
    }

    private String defaultGreeting(Map<String, Object> tutor) {
        return "你好，我是" + str(tutor.get("tutorName")) + "老师的 AI 分身，可以聊聊"
                + str(tutor.get("researchDirection")) + "方向、项目要求与面试准备。";
    }

    private String defaultKnowledge(Map<String, Object> tutor) {
        return "导师：" + str(tutor.get("tutorName")) + "（" + str(tutor.get("title")) + "）\n"
                + "实训室：" + str(tutor.get("labName")) + "\n"
                + "研究方向：" + str(tutor.get("researchDirection")) + "\n"
                + "导师介绍：" + str(tutor.get("intro")) + "\n"
                + "招生名额：" + str(tutor.get("quota")) + "，剩余：" + str(tutor.get("remainingQuota"));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /* ---------------- 通用 ---------------- */

    private Map<String, Object> envelope(String kind, Map<String, Object> data, Map<String, Object> request,
                                         Long studentId, Long tutorId, Long applicationId) {
        try {
            jdbc.update("""
                    INSERT INTO ai_reviews (kind, student_id, tutor_id, application_id, engine, model, request, response)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, kind, studentId, tutorId, applicationId,
                    gateway.provider(), gateway.model(),
                    objectMapper.writeValueAsString(request), objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.warn("AI 调用留痕失败（不影响结果返回）：{}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", gateway.provider());
        result.put("provider", gateway.activeName());
        result.put("model", gateway.model());
        result.put("placeholder", "mock".equals(gateway.provider()));
        result.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        result.put("data", data);
        return result;
    }

    private int choiceIndex(Map<String, Object> app, Long tutorId) {
        if (tutorId.equals(asLong(app.get("tutor1Id")))) {
            return 1;
        }
        return tutorId.equals(asLong(app.get("tutor2Id"))) ? 2 : 3;
    }

    private Long currentTutorId() {
        Long userId = UserContext.get().id();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM tutors WHERE user_id = ?", userId);
        if (rows.isEmpty()) {
            throw new BizException(403, "当前账号不是导师，无法使用简历初评");
        }
        return asLong(rows.get(0).get("id"));
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String text(Map<String, Object> body, String key, String fallback) {        Object value = body.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
