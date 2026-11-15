package com.hbsd.labselect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.ocr.ResumeTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结构化简历草稿：AI 优化简历的编辑底稿。
 *
 * <p>与 {@link ResumeService} 的分工：ResumeService 管原始 PDF/DOC/DOCX 文件，
 * 本服务管“可编辑、可保存、可被 AI 逐栏改写”的文本。两者通过「从已上传简历导入」衔接，
 * 草稿独立保存，不会反向覆盖学生上传的文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeDraftService {

    /** 可优化的简历栏目。顺序即前端编辑区的展示顺序。 */
    public record SectionDef(String key, String title, String hint) {
    }

    public static final List<SectionDef> SECTIONS = List.of(
            new SectionDef("basic", "个人信息", "姓名、联系方式、求职意向；一页简历的第一屏"),
            new SectionDef("education", "教育背景", "学校、专业、时间、GPA、核心课程与荣誉"),
            new SectionDef("projects", "项目经历", "每个项目写清背景、你负责的部分、技术方案与结果"),
            new SectionDef("internships", "实习经历", "公司、岗位、时间、职责与可量化产出"),
            new SectionDef("skills", "技能描述", "只写能被追问 3 层的技能，标注熟练程度"),
            new SectionDef("awards", "奖项证书", "竞赛、奖学金、职业资格与证书"),
            new SectionDef("selfIntro", "自我评价", "用 3~4 句说明优势与方向匹配，不写空话"),
            new SectionDef("other", "其他信息", "开源项目、社团经历、作品链接等补充材料"));

    public static final Set<String> SECTION_KEYS = SECTIONS.stream()
            .map(SectionDef::key)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public static final List<String> FOCUS_KEYS = List.of("concise", "quantify", "keywords", "professional");

    public static final int MAX_SECTION_CHARS = 2000;
    public static final int MAX_TOTAL_CHARS = 8000;
    public static final int MIN_OPTIMIZE_CHARS = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResumeTextService resumeTextService;

    /* ---------------- 读取与保存 ---------------- */

    public Map<String, Object> load(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT content, source, updated_at AS updatedAt
                  FROM resume_drafts WHERE student_id = ?
                """, studentId);
        Map<String, Object> stored = rows.isEmpty() ? Map.of() : parse(String.valueOf(rows.get(0).get("content")));
        Map<String, Object> draft = normalize(stored);
        draft.put("source", rows.isEmpty() ? "manual" : String.valueOf(rows.get(0).get("source")));
        draft.put("updatedAt", rows.isEmpty() ? null : rows.get(0).get("updatedAt"));

        Map<String, Object> resume = new LinkedHashMap<>();
        List<Map<String, Object>> resumeRows = jdbc.queryForList("""
                SELECT file_name AS fileName, uploaded_at AS uploadedAt
                  FROM student_resumes WHERE student_id = ?
                """, studentId);
        resume.put("uploaded", !resumeRows.isEmpty());
        if (!resumeRows.isEmpty()) {
            resume.putAll(resumeRows.get(0));
        }
        draft.put("resume", resume);
        draft.put("limits", limits());
        return draft;
    }

    /**
     * 保存草稿。所有长度限制在这里兜底：前端虽有计数器，但抓包直调同样要被拒绝。
     */
    public Map<String, Object> save(Long studentId, Map<String, Object> body) {
        Map<String, Object> normalized = normalize(body);
        validate(normalized);

        try {
            String json = objectMapper.writeValueAsString(contentOf(normalized));
            String source = text(body.get("source"), "manual");
            if (!List.of("manual", "resume-text").contains(source)) {
                source = "manual";
            }
            jdbc.update("""
                    INSERT INTO resume_drafts (student_id, content, source)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE content = VALUES(content), source = VALUES(source)
                    """, studentId, json, source);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("简历草稿保存失败：" + e.getMessage());
        }
        return load(studentId);
    }

    /* ---------------- 从已上传简历导入 ---------------- */

    /**
     * 把已上传简历的正文按常见栏目切分，填入草稿。
     *
     * <p>只做规则切分、不调用大模型：导入是“给用户一个可编辑的起点”，
     * 栏目识别不完美时用户可以直接改，成本远低于为一次导入调用模型。</p>
     */
    public Map<String, Object> importFromResume(Long studentId) {
        ResumeTextService.Extract extract = resumeTextService.extract(studentId);
        if (!extract.hasText()) {
            throw new BizException("尚未上传简历，或简历正文未能解析。请先上传 PDF / DOCX 简历，或直接在页面手动填写。");
        }
        String text = extract.text().trim();
        if (text.length() < 10) {
            throw new BizException("简历正文过短（" + text.length() + " 字），无法提取有效内容，请手动填写。");
        }

        Map<String, StringBuilder> buckets = splitByHeadings(text);
        Map<String, String> contents = new LinkedHashMap<>();
        List<String> matchedTitles = new ArrayList<>();
        for (SectionDef def : SECTIONS) {
            StringBuilder bucket = buckets.get(def.key());
            String content = bucket == null ? "" : bucket.toString().trim();
            contents.put(def.key(), content);
            if (!content.isBlank()) {
                matchedTitles.add(def.title());
            }
        }
        // 没识别到任何栏目：整份正文放进「其他信息」，至少不丢内容
        boolean matched = contents.values().stream().anyMatch(v -> !v.isBlank());
        if (!matched) {
            contents.put("other", text);
        }
        // 个人信息为空时，用学籍档案补齐可自动填的字段
        boolean profilePrefilled = false;
        if (contents.get("basic").isBlank()) {
            contents.put("basic", basicFromProfile(studentId));
            profilePrefilled = !contents.get("basic").isBlank();
        }

        int total = 0;
        boolean truncated = false;
        List<Map<String, Object>> sections = new ArrayList<>();
        for (SectionDef def : SECTIONS) {
            String content = clean(contents.get(def.key()));
            if (total + content.length() > MAX_TOTAL_CHARS) {
                int room = Math.max(0, MAX_TOTAL_CHARS - total);
                content = content.length() > room ? content.substring(0, room).trim() : content;
                truncated = true;
            }
            if (content.length() > MAX_SECTION_CHARS) {
                content = content.substring(0, MAX_SECTION_CHARS).trim();
                truncated = true;
            }
            total += content.length();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", def.key());
            row.put("content", content);
            sections.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sections", sections);
        body.put("targetRole", "");
        body.put("keywords", List.of());
        body.put("focus", FOCUS_KEYS);
        body.put("source", "resume-text");
        Map<String, Object> saved = save(studentId, body);
        saved.put("imported", true);
        saved.put("matchedSections", matchedTitles);
        saved.put("profilePrefilled", profilePrefilled);
        saved.put("resumeTextChars", extract.chars());
        saved.put("resumeTextEngine", extract.engine());
        saved.put("truncated", truncated);
        return saved;
    }

    /* ---------------- 供 AI 编排使用 ---------------- */

    /** 取出指定栏目的正文；未指定时返回所有非空栏目。 */
    public List<Map<String, Object>> pickSections(Map<String, Object> draft, List<String> keys) {
        Set<String> wanted = keys == null || keys.isEmpty()
                ? SECTION_KEYS
                : keys.stream().filter(SECTION_KEYS::contains).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> section : sectionsOf(draft)) {
            String key = String.valueOf(section.get("key"));
            String content = String.valueOf(section.getOrDefault("content", ""));
            if (!wanted.contains(key) || content.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("title", section.get("title"));
            row.put("content", content);
            result.add(row);
        }
        return result;
    }

    public Map<String, Object> limits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxSectionChars", MAX_SECTION_CHARS);
        limits.put("maxTotalChars", MAX_TOTAL_CHARS);
        limits.put("minChars", MIN_OPTIMIZE_CHARS);
        return limits;
    }

    public String targetRoleOf(Map<String, Object> draft) {
        return text(draft.get("targetRole"), "");
    }

    @SuppressWarnings("unchecked")
    public List<String> keywordsOf(Map<String, Object> draft) {
        Object value = draft.get("keywords");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String word = text(item, "");
            if (!word.isBlank() && !result.contains(word)) {
                result.add(word);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<String> focusOf(Map<String, Object> draft) {
        Object value = draft.get("focus");
        if (!(value instanceof List<?> list)) {
            return FOCUS_KEYS;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String key = text(item, "");
            if (FOCUS_KEYS.contains(key) && !result.contains(key)) {
                result.add(key);
            }
        }
        return result.isEmpty() ? FOCUS_KEYS : result;
    }

    /* ---------------- 归一化与校验 ---------------- */

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, String> contents = new LinkedHashMap<>();
        Object rawSections = source.get("sections");
        if (rawSections instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String key = text(map.get("key"), "");
                    if (SECTION_KEYS.contains(key)) {
                        contents.put(key, clean(text(map.get("content"), "")));
                    }
                }
            }
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        for (SectionDef def : SECTIONS) {
            String content = contents.getOrDefault(def.key(), "");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", def.key());
            row.put("title", def.title());
            row.put("hint", def.hint());
            row.put("content", content);
            row.put("chars", content.length());
            sections.add(row);
        }

        List<String> keywords = new ArrayList<>();
        Object rawKeywords = source.get("keywords");
        if (rawKeywords instanceof List<?> list) {
            for (Object item : list) {
                String word = text(item, "").replaceAll("\\s+", " ");
                if (!word.isBlank() && !keywords.contains(word) && keywords.size() < 12) {
                    keywords.add(word);
                }
            }
        }

        List<String> focus = new ArrayList<>();
        Object rawFocus = source.get("focus");
        if (rawFocus instanceof List<?> list) {
            for (Object item : list) {
                String key = text(item, "");
                if (FOCUS_KEYS.contains(key) && !focus.contains(key)) {
                    focus.add(key);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sections);
        result.put("targetRole", text(source.get("targetRole"), ""));
        result.put("keywords", keywords);
        result.put("focus", focus);
        result.put("source", text(source.get("source"), "manual"));
        return result;
    }

    private void validate(Map<String, Object> draft) {
        int total = 0;
        for (Map<String, Object> section : sectionsOf(draft)) {
            String title = text(section.get("title"), "栏目");
            String content = text(section.get("content"), "");
            if (content.length() > MAX_SECTION_CHARS) {
                throw new BizException(400,
                        "「" + title + "」超过 " + MAX_SECTION_CHARS + " 字，请精简后再保存");
            }
            total += content.length();
        }
        if (total > MAX_TOTAL_CHARS) {
            throw new BizException(400, "简历总字数超过 " + MAX_TOTAL_CHARS + " 字，请精简后再保存");
        }
        String targetRole = text(draft.get("targetRole"), "");
        if (targetRole.length() > 60) {
            throw new BizException(400, "目标岗位不能超过 60 个字");
        }
    }

    private Map<String, Object> contentOf(Map<String, Object> draft) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("sections", sectionsOf(draft).stream().map(section -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", section.get("key"));
            row.put("content", section.get("content"));
            return row;
        }).toList());
        content.put("targetRole", draft.get("targetRole"));
        content.put("keywords", draft.get("keywords"));
        content.put("focus", draft.get("focus"));
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sectionsOf(Map<String, Object> draft) {
        Object value = draft.get("sections");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("简历草稿解析失败，按空草稿处理：{}", e.getMessage());
            return Map.of();
        }
    }

    /* ---------------- 正文切分 ---------------- */

    private Map<String, StringBuilder> splitByHeadings(String text) {
        Map<String, StringBuilder> buckets = new LinkedHashMap<>();
        String current = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (current != null) {
                    append(buckets, current, "");
                }
                continue;
            }
            String key = headingKey(line);
            if (key != null) {
                current = key;
                continue;
            }
            append(buckets, current == null ? "other" : current, line);
        }
        return buckets;
    }

    /** 识别常见简历栏目标题；正文句子里的“项目经历”不会被误判（限制行宽与标点）。 */
    private String headingKey(String line) {
        String value = line.replaceAll("^[#\\s]+", "").trim();
        if (value.isEmpty() || value.length() > 24) {
            return null;
        }
        if (value.endsWith("。") || value.endsWith("；") || value.endsWith("，")) {
            return null;
        }
        if (value.startsWith("•") || value.startsWith("-") || value.startsWith("*")) {
            return null;
        }
        if (value.matches("^\\d+[.、].*")) {
            return null;
        }
        String compact = value.replaceAll("[\\s:：|｜/]+", "");
        if (containsAny(compact, "个人信息", "基本信息", "联系方式", "个人资料", "求职意向")) {
            return "basic";
        }
        if (containsAny(compact, "教育背景", "教育经历", "学习经历", "教育情况")) {
            return "education";
        }
        if (containsAny(compact, "项目经历", "项目经验", "主要项目", "科研经历", "项目实践")) {
            return "projects";
        }
        if (containsAny(compact, "实习经历", "实习经验", "工作经历", "实践经历", "实习实践")) {
            return "internships";
        }
        if (containsAny(compact, "专业技能", "技能描述", "技能特长", "技术栈", "掌握技能", "技能清单")) {
            return "skills";
        }
        if (containsAny(compact, "奖项", "荣誉", "获奖", "证书", "资格证书")) {
            return "awards";
        }
        if (containsAny(compact, "自我评价", "个人评价", "自我介绍", "个人总结", "自我描述")) {
            return "selfIntro";
        }
        if (containsAny(compact, "其他信息", "附加信息", "补充信息", "其他")) {
            return "other";
        }
        return null;
    }

    private String basicFromProfile(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.name, u.student_no AS studentNo, sp.major, sp.class_name AS className,
                       sp.grade, sp.gpa
                  FROM users u LEFT JOIN student_profiles sp ON sp.user_id = u.id
                 WHERE u.id = ?
                """, studentId);
        if (rows.isEmpty()) {
            return "";
        }
        Map<String, Object> row = rows.get(0);
        List<String> lines = new ArrayList<>();
        lines.add("姓名：" + text(row.get("name"), ""));
        lines.add("学号：" + text(row.get("studentNo"), ""));
        lines.add("专业：" + text(row.get("major"), ""));
        lines.add("班级：" + text(row.get("className"), ""));
        lines.add("年级：" + text(row.get("grade"), ""));
        lines.add("GPA：" + text(row.get("gpa"), ""));
        lines.add("电话：");
        lines.add("邮箱：");
        lines.add("求职意向：");
        return String.join("\n", lines);
    }

    private void append(Map<String, StringBuilder> buckets, String key, String line) {
        StringBuilder builder = buckets.computeIfAbsent(key, k -> new StringBuilder());
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
