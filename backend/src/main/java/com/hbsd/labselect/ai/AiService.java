package com.hbsd.labselect.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.ocr.ResumeTextService;
import com.hbsd.labselect.service.PhaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

        Map<String, Object> data = gateway.avatarChat(input);
        Long studentId = UserContext.get().id();
        return envelope("AVATAR_CHAT", data,
                Map.of("tutorId", tutorId, "question", question), studentId, tutorId, null);
    }

    /* ---------------- 教师端 ---------------- */

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
