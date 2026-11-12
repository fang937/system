package com.hbsd.labselect.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 占位实现：不调用任何外部模型，用可解释的规则产出结构化结果。
 *
 * <p>目的不是"像 AI"，而是把输出结构、字段语义、评分口径先定下来：
 * 前端按此渲染、评分按此落库；将来换成真实模型时结构不变，
 * 只把本类替换为 AiGateway 的另一个实现即可。</p>
 */
public class MockAiGateway implements AiGateway {

    /** 技能 → 方向关键词的朴素映射：占位阶段避免"全 0 命中"导致推荐没有区分度。 */
    private static final Map<String, List<String>> SKILL_HINTS = Map.ofEntries(
            Map.entry("java", List.of("后端", "分布式", "平台", "工程")),
            Map.entry("python", List.of("python", "数据", "机器学习", "大模型", "挖掘")),
            Map.entry("算法", List.of("算法", "机器学习", "视觉", "推荐")),
            Map.entry("机器学习", List.of("机器学习", "智能", "推荐", "视觉")),
            Map.entry("vue", List.of("可视化", "前端", "交互")),
            Map.entry("前端", List.of("可视化", "前端", "交互")),
            Map.entry("spark", List.of("分布式", "大数据", "数据")),
            Map.entry("sql", List.of("数据", "挖掘", "平台")),
            Map.entry("数据分析", List.of("数据", "挖掘", "可视化")),
            Map.entry("linux", List.of("安全", "攻防", "密码")),
            Map.entry("ctf", List.of("攻防", "安全", "漏洞")),
            Map.entry("c++", List.of("视觉", "算法", "工程")),
            Map.entry("opencv", List.of("视觉", "图像")),
            Map.entry("go", List.of("分布式", "微服务", "平台")),
            Map.entry("微服务", List.of("分布式", "平台", "工程")));

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "rule-engine-v1";
    }

    /* ---------------- 学生端：面试技巧分析 ---------------- */

    @Override
    public Map<String, Object> interviewTips(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        List<Map<String, Object>> rubric = list(input.get("rubric"));
        List<Map<String, Object>> tutors = list(input.get("tutors"));

        List<String> skills = splitTags(str(student, "skillTags"));
        String major = defaultIfBlank(str(student, "major"), "本专业");
        Map<String, Object> target = tutors.isEmpty() ? Map.of() : tutors.get(0);
        String labName = defaultIfBlank(str(target, "labName"), "目标实训室");

        List<Map<String, Object>> focus = new ArrayList<>();
        for (Map<String, Object> item : rubric) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", str(item, "code"));
            row.put("name", str(item, "name"));
            row.put("weight", item.get("weight"));
            row.put("focus", str(item, "description"));
            focus.add(row);
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        for (String skill : skills.stream().limit(2).toList()) {
            questions.add(question(
                    "请挑一个你用 " + skill + " 完成的项目，说明你负责的部分和最难的取舍。",
                    "验证「实践技能」是否真实具备，而非仅停留在名词层面",
                    List.of("项目背景与目标", "你负责的模块与你做的关键决策", "踩到的坑与修复过程", "如果重做会改什么")));
        }
        questions.add(question(
                "你为什么选择" + labName + "？看过我们方向的什么资料？",
                "判断「动机匹配」：是了解后的选择，还是只看名额",
                List.of("从哪个具体问题/场景产生兴趣", "读过或跑过什么（论文、开源项目、课程）", "你希望在这里获得什么")));
        questions.add(question(
                "如果项目进度落后两周，你会怎么处理？",
                "考察「学习潜力」与「表达沟通」：拆解、沟通、取舍",
                List.of("先定位滞后原因（需求/技术/协作）", "列出可裁剪范围与最小可交付", "主动同步风险与新的时间点")));

        List<Map<String, Object>> tips = List.of(
                tip("用 STAR 讲项目", "背景-任务-行动-结果，控制在 90 秒内，结果要有数字或对比。"),
                tip("每个技术点准备一句「为什么」", "为什么选这个方案、为什么不选另一个，这是区分「做过」和「懂了」的关键。"),
                tip("把" + major + "课程知识挂到项目上", "面试官更关心你能否把课堂概念用到真实问题里。"),
                tip("不会就说不会，再给出思路", "诚实 + 可验证的推理，比硬编答案得分更高。"));

        List<String> selfCheck = List.of(
                "能用 1 分钟讲清一个项目的目标、你的角色、结果",
                "能说出" + labName + "方向的 2 个真实应用场景",
                "能解释简历里每个技术名词的含义与使用场景",
                "准备好 1 个失败案例，以及你从中学到什么");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", "结合你的技能标签与目标方向，面试重点会落在「实践技能」与「动机匹配」两项；"
                + "建议用 2 个项目 + 1 个失败案例的结构准备。");
        data.put("skills", skills);
        data.put("focus", focus);
        data.put("questions", questions);
        data.put("tips", tips);
        data.put("selfCheck", selfCheck);
        return data;
    }

    /* ---------------- 学生端：实训室推荐 ---------------- */

    @Override
    public Map<String, Object> recommend(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        List<Map<String, Object>> tutors = list(input.get("tutors"));
        List<String> skills = splitTags(str(student, "skillTags"));
        double gpa = num(student, "gpa", 0);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> tutor : tutors) {
            String text = (str(tutor, "researchDirection") + " " + str(tutor, "intro") + " "
                    + str(tutor, "labDescription") + " " + str(tutor, "labName")).toLowerCase();
            List<String> hits = new ArrayList<>();
            for (String skill : skills) {
                if (matches(text, skill)) {
                    hits.add(skill);
                }
            }

            int remaining = (int) num(tutor, "remainingQuota", 0);
            int quota = (int) num(tutor, "quota", 0);
            int applicants = (int) num(tutor, "applicantCount", 0);

            int skillScore = Math.min(40, hits.size() * 14);
            int quotaScore = remaining > 0 ? 20 : 0;
            double pressure = quota <= 0 ? 2.0 : (double) applicants / quota;
            int competeScore = (int) Math.max(0, Math.min(25, Math.round(25 - pressure * 6)));
            int gpaScore = gpa >= 3.7 ? 15 : gpa >= 3.4 ? 12 : gpa >= 3.1 ? 9 : 6;
            int total = Math.min(100, skillScore + quotaScore + competeScore + gpaScore);

            List<String> reasons = new ArrayList<>();
            if (!hits.isEmpty()) {
                reasons.add("技能命中：" + String.join(" / ", hits));
            }
            reasons.add(remaining > 0 ? "还有 " + remaining + " 个名额" : "名额已满，仅可作为递补目标");
            reasons.add("当前报考 " + applicants + " 人，竞争度" + (pressure >= 1 ? "偏高" : pressure >= 0.5 ? "适中" : "较低"));
            if (gpa >= 3.4) {
                reasons.add("GPA " + gpa + " 在竞争中有优势");
            }

            List<String> risks = new ArrayList<>();
            if (remaining <= 0) {
                risks.add("名额已锁定，除非有人放弃否则无法录取");
            }
            if (hits.isEmpty()) {
                risks.add("技能标签与该方向关键词重合度低，需在面试中补足动机说明");
            }
            if (pressure >= 1.5) {
                risks.add("报考人数已超过名额，建议同时准备第 2、3 志愿");
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tutorId", tutor.get("tutorId"));
            row.put("tutorName", str(tutor, "tutorName"));
            row.put("title", str(tutor, "title"));
            row.put("labId", tutor.get("labId"));
            row.put("labName", str(tutor, "labName"));
            row.put("researchDirection", str(tutor, "researchDirection"));
            row.put("matchScore", total);
            row.put("skillHits", hits);
            row.put("reasons", reasons);
            row.put("risks", risks);
            items.add(row);
        }

        items.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("matchScore")).reversed());
        for (int i = 0; i < items.size(); i++) {
            items.get(i).put("suggestedRank", i + 1);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", "依据技能关键词、名额余量与竞争度给出排序；建议前 3 位分别覆盖「冲一冲 / 稳妥 / 保底」。");
        data.put("skills", skills);
        data.put("items", items.size() > 8 ? items.subList(0, 8) : items);
        return data;
    }

    /* ---------------- 教师端：简历初评 ---------------- */

    @Override
    public Map<String, Object> resumeReview(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        Map<String, Object> application = map(input.get("application"));
        Map<String, Object> tutor = map(input.get("tutor"));
        List<Map<String, Object>> rubric = list(input.get("rubric"));

        String name = defaultIfBlank(str(student, "name"), "该同学");
        double gpa = num(student, "gpa", 0);
        List<String> skills = splitTags(str(student, "skillTags"));
        int choiceIndex = (int) num(application, "choiceIndex", 1);

        Map<String, Object> resume = map(input.get("resume"));
        boolean resumeUploaded = Boolean.TRUE.equals(resume.get("uploaded"));
        String resumeText = str(resume, "text");
        List<String> resumeKeywords = resumeKeywords(resumeText);

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("MAJOR", clamp((int) Math.round(gpa / 4.0 * 100)));
        scores.put("SKILL", clamp(58 + skills.size() * 9 + Math.min(15, resumeKeywords.size() * 3)));
        scores.put("POTENTIAL", clamp(70 + (skills.size() >= 3 ? 8 : 0) + (gpa >= 3.5 ? 6 : 0)));
        scores.put("MOTIVE", choiceIndex == 1 ? 86 : choiceIndex == 2 ? 78 : 70);
        scores.put("COMM", 75);

        List<Map<String, Object>> dimensions = new ArrayList<>();
        double weighted = 0;
        for (Map<String, Object> item : rubric) {
            String code = str(item, "code");
            int score = scores.getOrDefault(code, 75);
            int weight = (int) num(item, "weight", 0);
            weighted += score * weight / 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("name", str(item, "name"));
            row.put("weight", weight);
            row.put("score", score);
            row.put("reason", dimensionReason(code, score, gpa, skills, choiceIndex));
            row.put("confidence", "COMM".equals(code) ? "LOW" : "MEDIUM");
            dimensions.add(row);
        }

        List<String> highlights = new ArrayList<>();
        if (gpa >= 3.5) {
            highlights.add("GPA " + gpa + "，专业基础较扎实");
        }
        if (!skills.isEmpty()) {
            highlights.add("技能标签：" + String.join("、", skills));
        }
        if (choiceIndex == 1) {
            highlights.add("第一志愿即为本方向，动机明确");
        }
        if (resumeUploaded) {
            if (!resumeKeywords.isEmpty()) {
                List<String> shown = resumeKeywords.subList(0, Math.min(5, resumeKeywords.size()));
                highlights.add("简历正文已解析（" + resumeText.length() + " 字），提到：" + String.join("、", shown));
            } else {
                highlights.add("已上传简历：" + defaultIfBlank(str(resume, "fileName"), "附件") + "，建议面试前通读");
            }
        }
        if (highlights.isEmpty()) {
            highlights.add("档案信息较少，建议面试重点核实");
        }

        List<String> risks = new ArrayList<>();
        if (!resumeUploaded) {
            risks.add("尚未上传简历，建议面试前要求学生补交");
        } else if (resumeText.isBlank()) {
            risks.add("简历未解析出正文（可能是扫描件且未配置 OCR），建议面试前人工浏览");
        }
        if (skills.isEmpty()) {
            risks.add("未填写技能标签，无法预判动手能力");
        }
        if (gpa > 0 && gpa < 3.2) {
            risks.add("GPA 偏低，需确认是否由项目投入挤占课业");
        }
        risks.add("简历无项目细节，建议追问「你负责哪一部分」");
        risks.add("沟通维度无历史数据，需现场观察");

        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(question("挑一个你写在简历上的技能，讲一个用到它的具体场景。",
                "核实技能真实性", List.of("场景", "你的动作", "结果")));
        questions.add(question("在" + defaultIfBlank(str(tutor, "researchDirection"), "本方向") + "上，你最近自学了什么？",
                "验证学习潜力与动机", List.of("学什么", "怎么学", "遇到什么卡点")));
        questions.add(question("如果本次未录取，你的备选计划是什么？",
                "观察自我认知与韧性", List.of("备选方向", "差距认知")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headline", name + "：基础"
                + (gpa >= 3.5 ? "较好" : gpa >= 3.2 ? "中等" : "偏弱")
                + "，技能标签 " + skills.size() + " 项，第 " + choiceIndex + " 志愿");
        summary.put("suggestedTotal", Math.round(weighted * 10) / 10.0);
        summary.put("recommendation", weighted >= 80 ? "建议优先面试" : weighted >= 70 ? "正常面试，重点核实技能" : "谨慎，需核实基础");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("highlights", highlights);
        data.put("risks", risks);
        data.put("suggestedQuestions", questions);
        data.put("dimensions", dimensions);
        data.put("disclaimer", "以上为基于档案的规则初评，仅作面试提问与打分参考，不能替代导师判断。");
        return data;
    }

    /* ---------------- 学生端：导师 AI 分身 ---------------- */

    @Override
    public Map<String, Object> avatarChat(Map<String, Object> input) {
        Map<String, Object> persona = map(input.get("persona"));
        Map<String, Object> tutor = map(input.get("tutor"));
        String question = str(input, "question");

        String direction = defaultIfBlank(str(tutor, "researchDirection"), "本实训室方向");
        String intro = defaultIfBlank(str(tutor, "intro"), "老师的研究方向与项目情况会在开放日介绍。");
        String labName = defaultIfBlank(str(tutor, "labName"), "本实训室");
        int remaining = (int) num(tutor, "remainingQuota", 0);

        String answer;
        List<String> citations = new ArrayList<>();
        if (containsAny(question, "方向", "研究", "做什么", "项目")) {
            answer = "我们组主要围绕" + direction + "展开。" + intro
                    + "如果你对其中某一块感兴趣，可以准备一个相关的小项目或课程作业来聊。";
            citations.add("研究方向：" + direction);
            citations.add("导师介绍：" + intro);
        } else if (containsAny(question, "要求", "准备", "看重", "需要", "零基础")) {
            answer = "比起现有技能，我们更看重两点：一是能不能把一个问题拆开、自己找到资料；"
                    + "二是愿不愿意把代码写到能跑、能复现。" + labName + "的日常以项目推进为主，"
                    + "建议你先用" + direction + "做一个最小可运行的小东西再来聊。";
            citations.add("方向要求：" + direction);
        } else if (containsAny(question, "名额", "竞争", "几个人", "录取")) {
            answer = "目前系统显示剩余名额 " + remaining + " 个。具体的录取结果由面试与双选流程决定，"
                    + "我不能替老师做任何承诺，也不方便评价其他同学的情况。";
            citations.add("剩余名额：" + remaining);
        } else if (containsAny(question, "面试", "流程", "问什么", "考察")) {
            answer = "面试会围绕专业基础、实践技能、学习潜力、动机匹配、表达沟通五个维度展开，"
                    + "重点是你做过什么、怎么做的、遇到问题怎么解决。具体题目不便透露，"
                    + "但准备好 1~2 个能讲透的项目基本就够用了。";
            citations.add("评分维度：专业基础 / 实践技能 / 学习潜力 / 动机匹配 / 表达沟通");
        } else {
            answer = "这个问题我掌握的信息还不够，建议在开放日直接问" + defaultIfBlank(str(persona, "displayName"), "老师")
                    + "。我能确定的是：" + intro;
            citations.add("导师介绍：" + intro);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("citations", citations);
        data.put("boundary", defaultIfBlank(str(persona, "boundaries"),
                "不承诺录取结果、不评价其他导师、不透露面试题目"));
        return data;
    }

    /* ---------------- 内部工具 ---------------- */

    private static final List<String> TECH_KEYWORDS = List.of(
            "Python", "Java", "Vue", "React", "Spring", "MySQL", "SQL", "Redis", "Docker", "Linux",
            "PyTorch", "TensorFlow", "OpenCV", "Spark", "Hadoop", "Git", "Go", "C++", "CTF",
            "机器学习", "深度学习", "大数据", "数据分析", "神经网络", "计算机视觉", "自然语言处理",
            "网络安全", "微服务", "分布式", "前端", "后端", "算法", "竞赛", "论文", "专利", "实习");

    /** 从简历正文里提取技术关键词（占位实现，真实模型可自行理解长文本） */
    private List<String> resumeKeywords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase();
        List<String> hits = new ArrayList<>();
        for (String keyword : TECH_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                hits.add(keyword);
            }
        }
        return hits;
    }

    private String dimensionReason(String code, int score, double gpa, List<String> skills, int choiceIndex) {
        return switch (code) {
            case "MAJOR" -> gpa > 0 ? "GPA " + gpa + " 折算" : "缺少成绩数据，按中位估计";
            case "SKILL" -> skills.isEmpty() ? "无技能标签，按中位估计" : "技能标签 " + skills.size() + " 项";
            case "POTENTIAL" -> "依据 GPA 与技能广度推断，需面试确认";
            case "MOTIVE" -> "第 " + choiceIndex + " 志愿，按志愿顺序推断";
            default -> "无历史面试数据，按中位估计，建议现场校准";
        };
    }

    private Map<String, Object> question(String q, String intent, List<String> outline) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", q);
        row.put("intent", intent);
        row.put("outline", outline);
        return row;
    }

    private Map<String, Object> tip(String title, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private double num(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private List<String> splitTags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String part : text.split("[,，;；/、|]")) {
            String tag = part.trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    /** 技能是否命中方向文本：先直配，再按技能词表扩展到同类方向词。 */
    private boolean matches(String text, String skill) {
        if (skill == null || skill.isBlank()) {
            return false;
        }
        String key = skill.toLowerCase();
        if (text.contains(key)) {
            return true;
        }
        for (Map.Entry<String, List<String>> entry : SKILL_HINTS.entrySet()) {
            if (key.contains(entry.getKey())) {
                for (String hint : entry.getValue()) {
                    if (text.contains(hint)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
