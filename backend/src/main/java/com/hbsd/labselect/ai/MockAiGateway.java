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
        List<Map<String, Object>> resumeSections = resumeSections(resumeText, resumeKeywords);

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
        data.put("resumeSections", resumeSections);
        data.put("dimensions", dimensions);
        data.put("disclaimer", "以上为基于档案的规则初评，仅作面试提问与打分参考，不能替代导师判断。");
        return data;
    }

    /**
     * 按标准简历模板（基本信息 / 专业技能 / 项目经历 / 教育背景 / 实践荣誉 / 自我评价）
     * 对正文做规则打分。没有模型时也能给导师一个「简历写到什么程度」的结构化视图。
     */
    private List<Map<String, Object>> resumeSections(String text, List<String> keywords) {
        String t = text == null ? "" : text;
        boolean blank = t.isBlank();
        List<Map<String, Object>> list = new ArrayList<>();

        int basic = blank ? 0 : (t.contains("@") && t.matches("(?s).*\\d{7,}.*") ? 85 : 65);
        int skills = blank ? 0 : clamp(55 + Math.min(35, keywords.size() * 3)
                + (containsAny(t, "精通", "熟练", "深入") ? 5 : 0));
        int digits = countAny(t, "提升", "降低", "优化", "从 ", "%", "ms", "倍");
        int projects = blank ? 0 : clamp(55 + Math.min(25, countWord(t, "项目") * 5)
                + Math.min(10, digits * 2) + (containsAny(t, "负责", "主导", "独立") ? 5 : 0));
        int education = blank ? 0 : (containsAny(t, "大学", "学院", "学校") ? 80 : 55);
        int practice = blank ? 0 : (containsAny(t, "奖", "荣誉", "班长", "社团", "竞赛") ? 80 : 60);
        int summary = blank ? 0 : (containsAny(t, "自我评价", "沟通", "学习", "热爱", "分享") ? 75 : 60);

        list.add(section("BASIC", "基本信息与求职意向", 10, basic,
                blank ? "未解析到正文" : "联系方式 / 求职意向", evidenceOf(t, "@", "电话", "邮箱")));
        list.add(section("SKILLS", "专业技能", 30, skills,
                blank ? "未解析到正文" : "识别到 " + keywords.size() + " 项技能关键词", evidenceOf(t, "技能", "熟悉", "掌握")));
        list.add(section("PROJECTS", "项目经历", 30, projects,
                blank ? "未解析到正文" : (digits > 0 ? "项目描述含量化结果" : "项目描述缺少量化结果"),
                evidenceOf(t, "项目", "负责", "优化")));
        list.add(section("EDUCATION", "教育背景", 10, education,
                blank ? "未解析到正文" : "学校 / 专业 / 学历", evidenceOf(t, "大学", "学院", "专业")));
        list.add(section("PRACTICE", "社会实践与荣誉", 10, practice,
                blank ? "未解析到正文" : "竞赛 / 荣誉 / 组织经历", evidenceOf(t, "奖", "荣誉", "班长")));
        list.add(section("SUMMARY", "自我评价与表达", 10, summary,
                blank ? "未解析到正文" : "自我认知 / 技术分享", evidenceOf(t, "自我评价", "沟通", "分享")));
        return list;
    }

    private Map<String, Object> section(String code, String name, int weight, int score, String comment, String evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("weight", weight);
        row.put("score", score);
        row.put("comment", comment);
        row.put("evidence", evidence);
        return row;
    }

    /** 取包含关键词的原文片段作为依据；没有则回退为字数说明。 */
    private String evidenceOf(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return "未解析到正文";
        }
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0) {
                int start = Math.max(0, index - 12);
                int end = Math.min(text.length(), index + 28);
                return text.substring(start, end).replaceAll("\\s+", " ").trim();
            }
        }
        return "正文 " + text.length() + " 字，未定位到对应版块关键词";
    }

    private int countWord(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) >= 0) {
            count++;
            index += word.length();
        }
        return count;
    }

    private int countAny(String text, String... words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word)) {
                count++;
            }
        }
        return count;
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
            String tutorName = defaultIfBlank(str(tutor, "tutorName"), "导师");
            answer = "我们组主要围绕" + direction + "展开。"
                    + "更细的项目、带教方式和日常节奏，开放日可以请" + tutorName + "老师当面讲讲。"
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
            String tutorName = defaultIfBlank(str(tutor, "tutorName"), "导师");
            answer = "这个问题我掌握的信息还不够，建议在开放日当面问" + tutorName + "老师本人，由老师亲自解答更准确。"
                    + "我能确定的是：" + tutorName + "老师的方向是" + direction + "。";
            citations.add("研究方向：" + direction);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("citations", citations);
        data.put("boundary", defaultIfBlank(str(persona, "boundaries"),
                "不承诺录取结果、不评价其他导师、不透露面试题目"));
        return data;
    }

    /* ---------------- 学生端：AI 优化简历 ---------------- */

    @Override
    public Map<String, Object> resumeOptimize(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        List<Map<String, Object>> sections = list(input.get("sections"));
        List<String> focus = stringList(input.get("focus"));
        List<String> keywords = stringList(input.get("keywords"));
        String targetRole = defaultIfBlank(str(input, "targetRole"),
                defaultIfBlank(str(student, "major"), "目标岗位"));

        List<Map<String, Object>> suggestions = new ArrayList<>();
        List<String> allText = new ArrayList<>();
        int beforeSum = 0;
        int afterSum = 0;
        int improved = 0;
        int issueCount = 0;

        for (Map<String, Object> section : sections) {
            String key = str(section, "key");
            String title = str(section, "title");
            String before = str(section, "content").trim();
            if (before.isBlank()) {
                continue;
            }
            allText.add(before);
            String after = rewriteSection(key, before, focus);
            int beforeScore = sectionScore(before, keywords);
            int afterScore = Math.max(beforeScore, sectionScore(after, keywords));
            List<String> issues = sectionIssues(key, before, keywords, focus);
            List<String> reasons = sectionReasons(focus, issues, before, after);
            boolean changed = !before.equals(after);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("section", key);
            row.put("sectionTitle", title);
            row.put("before", before);
            row.put("after", after);
            row.put("reasons", reasons);
            row.put("issues", issues);
            row.put("focus", focus);
            row.put("changed", changed);
            row.put("scoreBefore", beforeScore);
            row.put("scoreAfter", afterScore);
            suggestions.add(row);

            beforeSum += beforeScore;
            afterSum += afterScore;
            issueCount += issues.size();
            if (changed || !issues.isEmpty()) {
                improved++;
            }
        }

        int count = suggestions.size();
        int scoreBefore = count == 0 ? 0 : Math.round((float) beforeSum / count);
        int scoreAfter = count == 0 ? 0 : Math.round((float) afterSum / count);
        String headline = count == 0
                ? "所选栏目暂无内容，请先填写简历"
                : improved == 0
                ? "整体结构较完整，建议只做轻度润色"
                : "共分析 " + count + " 个栏目，" + improved + " 个有明确改进空间";
        String advice = issueCount == 0
                ? "保持现有结构，重点核对岗位关键词是否已自然出现。"
                : "优先补「可量化结果」，再把长段落拆成 2~3 条要点；带「补充：」的内容需要你填真实数据。";

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scoreBefore", scoreBefore);
        summary.put("scoreAfter", scoreAfter);
        summary.put("sectionCount", count);
        summary.put("headline", headline);
        summary.put("advice", advice);

        Map<String, Object> keywordMatches = keywordMatches(allText, keywords);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("suggestions", suggestions);
        data.put("keywordMatches", keywordMatches);
        data.put("targetRole", targetRole);
        data.put("focus", focus);
        data.put("disclaimer", "AI 只调整措辞与结构，不会新增你的经历；带「（补充：…）」的占位内容需要你填写真实数据后再保存。");
        return data;
    }

    /* ---------------- 学生端：AI 面试辅导 ---------------- */

    private static final List<String> COMMON_QUESTIONS = List.of(
            "你最大的优点和缺点分别是什么？",
            "讲一次你和同学意见不一致的经历，最后是怎么解决的？",
            "如果项目进度落后两周，你会怎么处理？",
            "你遇到过最难的技术问题是什么？你是怎么定位的？",
            "为什么选择这个方向，而不是更热门的方向？",
            "如果这次没有被录取，你的备选计划是什么？",
            "你最近自学了什么？怎么验证自己真的学会了？",
            "你如何安排课程学习与项目实践的时间？");

    @Override
    public Map<String, Object> interviewCoachQuestion(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        Map<String, Object> tutor = map(input.get("tutor"));
        String section = defaultIfBlank(str(input, "section"), "common");
        String role = defaultIfBlank(str(input, "role"),
                defaultIfBlank(str(tutor, "researchDirection"), "目标方向"));
        String labName = defaultIfBlank(str(tutor, "labName"), "目标实训室");
        int variant = Math.max(0, (int) num(input, "variant", 0));
        long studentId = (long) num(student, "id", 0);

        String question;
        String intent;
        List<String> tips;
        int seconds;
        String targetLabel = role;

        switch (section) {
            case "self-intro" -> {
                question = "请用 1 分钟做一个自我介绍：你是谁、做过什么、为什么选择" + labName + "？";
                intent = "考察表达结构、自我定位与方向匹配，不要求背模板";
                tips = List.of("结构：我是谁 → 我能做什么 → 我为什么来",
                        "用 1 个项目证明能力，别罗列课程名",
                        "结尾点出你与" + role + "的连接");
                seconds = 120;
                targetLabel = labName;
            }
            case "role" -> {
                String[] templates = {
                        "你了解" + role + "方向吗？结合你做过的一个项目，说说你为什么适合这个方向。",
                        "如果让你用三个月做一个与" + role + "相关的小项目，你会怎么选题、怎么拆解？",
                        "在" + role + "方向里，你遇到过最难的问题是什么？你是怎么定位和解决的？"
                };
                question = templates[variant % templates.length];
                intent = "验证对目标方向的了解程度、项目相关性与解决问题的思路";
                tips = List.of("先说自己理解的方向，再落到做过的项目",
                        "不会的部分给出学习路径，不硬编",
                        "准备 1 个与方向相关的具体问题反问");
                seconds = 120;
                targetLabel = labName + " · " + role;
            }
            default -> {
                int index = Math.floorMod((int) (studentId + variant), COMMON_QUESTIONS.size());
                question = COMMON_QUESTIONS.get(index);
                intent = "考察行为风格、沟通表达与自我认知，回答要有真实例子";
                tips = List.of("用 STAR 组织：背景-任务-行动-结果",
                        "控制在 90 秒内，留一个可被追问的细节");
                seconds = 90;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("section", section);
        data.put("sectionTitle", coachSectionTitle(section));
        data.put("question", question);
        data.put("intent", intent);
        data.put("tips", tips);
        data.put("timeLimitSeconds", seconds);
        data.put("targetLabel", targetLabel);
        return data;
    }

    @Override
    public Map<String, Object> interviewCoachFeedback(Map<String, Object> input) {
        Map<String, Object> student = map(input.get("student"));
        Map<String, Object> tutor = map(input.get("tutor"));
        String section = defaultIfBlank(str(input, "section"), "common");
        String role = defaultIfBlank(str(input, "role"),
                defaultIfBlank(str(tutor, "researchDirection"), "目标方向"));
        String labName = defaultIfBlank(str(tutor, "labName"), "目标实训室");
        String question = str(input, "question");
        String answer = str(input, "answer").trim();

        int length = answer.length();
        boolean structure = containsAny(answer, "首先", "其次", "最后", "第一", "第二", "第三", "①", "②", "1.", "2.", "\n•", "\n-");
        boolean numbers = answer.matches("(?s).*\\d.*");
        int matchHits = matchHits(answer, role, labName);
        int score = coachScore(length, structure, numbers, matchHits);

        Map<String, Object> structureDim = dimension("STRUCTURE", "结构与逻辑",
                structure ? clamp(82 + (length >= 160 ? 5 : 0)) : clamp(56 + length / 20),
                structure ? "有清晰的结构词或分点，面试官容易跟上" : "以平铺直叙为主，建议用 STAR 或「首先/其次/最后」");
        Map<String, Object> contentDim = dimension("CONTENT", "内容充实度",
                clamp(48 + length / 8 + (numbers ? 9 : 0)),
                numbers ? "有数据或结果支撑" : "细节偏少，建议补充规模、比例、耗时等可验证信息");
        Map<String, Object> expressionDim = dimension("EXPRESSION", "表达清晰度",
                clamp(60 + (structure ? 10 : 0) + (length >= 80 && length <= 600 ? 8 : 2)),
                length > 800 ? "内容偏长，口述容易失焦" : "句式完整，能听懂主线");
        Map<String, Object> matchDim = dimension("MATCH", "岗位匹配度",
                clamp(55 + matchHits * 8 + (containsAny(answer, labName, role) ? 8 : 0)),
                matchHits > 0 ? "回应了目标方向的关键词" : "与" + role + "的连接不足，建议点出 1 个相关课程或项目");

        List<String> strengths = new ArrayList<>();
        if (length >= 120) {
            strengths.add("回答有足够的信息量，能支撑 90 秒口述");
        }
        if (structure) {
            strengths.add("使用了清晰的结构词或分点，逻辑容易跟随");
        }
        if (numbers) {
            strengths.add("用数据或结果支撑，可信度更高");
        }
        if (matchHits > 0) {
            strengths.add("回应了目标方向的关键词");
        }
        if (strengths.isEmpty()) {
            strengths.add("能正面回应问题，没有回避");
        }

        List<String> improvements = new ArrayList<>();
        if (length < 80) {
            improvements.add("内容偏短：补充「背景 - 你的动作 - 结果」三层");
        }
        if (!structure) {
            improvements.add("结构不明显：用「首先/其次/最后」或 STAR 组织");
        }
        if (!numbers) {
            improvements.add("结果缺少量化：补充规模、比例、耗时等可验证数据");
        }
        if (matchHits == 0) {
            improvements.add("与目标方向的连接不足：点出 1 个相关课程或项目");
        }
        if (length > 800) {
            improvements.add("口述偏长：压到 90 秒内，只保留 2 个重点");
        }
        if (improvements.isEmpty()) {
            improvements.add("整体表达完整，可以继续练习追问与临场追问下的稳定性");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", score);
        data.put("level", coachLevel(score));
        data.put("dimensions", List.of(structureDim, contentDim, expressionDim, matchDim));
        data.put("strengths", strengths);
        data.put("improvements", improvements);
        data.put("sampleAnswer", sampleAnswer(section, student, role, labName));
        data.put("followUp", coachFollowUp(section, labName));
        data.put("answerChars", length);
        data.put("disclaimer", "反馈只针对这一次表达，不代表导师评价，也不预测录取结果。参考表达是结构模板，请替换为你的真实经历。");
        return data;
    }

    private Map<String, Object> dimension(String code, String name, int score, String comment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("score", clamp(score));
        row.put("comment", comment);
        return row;
    }

    private int coachScore(int length, boolean structure, boolean numbers, int matchHits) {
        int score = 48;
        if (length >= 60) {
            score += 10;
        }
        if (length >= 120) {
            score += 10;
        }
        if (length >= 220) {
            score += 6;
        }
        if (structure) {
            score += 10;
        }
        if (numbers) {
            score += 8;
        }
        score += Math.min(8, matchHits * 4);
        if (length > 800) {
            score -= 6;
        }
        if (length < 30) {
            score = Math.min(score, 45);
        }
        return clamp(score);
    }

    private String coachLevel(int score) {
        if (score >= 85) {
            return "优秀";
        }
        if (score >= 75) {
            return "良好";
        }
        return score >= 60 ? "合格" : "待提升";
    }

    private String coachSectionTitle(String section) {
        return switch (section) {
            case "self-intro" -> "自我介绍";
            case "role" -> "岗位相关问题模拟";
            default -> "常见问题演练";
        };
    }

    private int matchHits(String answer, String role, String labName) {
        String lab = labName.endsWith("实训室") ? labName.substring(0, labName.length() - 3) : labName;
        int hits = 0;
        for (String token : (role + " " + lab).split("[、，,;；/\\s]+")) {
            if (token.length() >= 2 && answer.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private String sampleAnswer(String section, Map<String, Object> student, String role, String labName) {
        String name = defaultIfBlank(str(student, "name"), "我");
        String major = defaultIfBlank(str(student, "major"), "本专业");
        if ("self-intro".equals(section)) {
            return "各位老师好，我是" + name + "，" + major + "专业。我想用三点介绍自己："
                    + "第一，……（课程或技能基础）；第二，……（最能代表你的项目：你负责什么、怎么做的、结果如何）；"
                    + "第三，……（为什么选择" + labName + "，与" + role + "的连接）。"
                    + "我的优势是……，希望在" + labName + "继续深入" + role + "。（请把省略号替换为你的真实经历）";
        }
        if ("role".equals(section)) {
            return "我对" + role + "的理解是……。我做过最接近的一件事是……（项目或课程：背景、你的动作、结果）。"
                    + "如果进入" + labName + "，我会先从……入手，用……验证自己是否适合这个方向。"
                    + "（请替换为真实经历，不要背模板）";
        }
        return "我会按「背景 - 任务 - 行动 - 结果」回答：当时的情况是……，我需要解决……，我具体做了……，"
                + "最后结果是……（尽量有数字）。这件事让我意识到……，之后我在……方面做了改进。"
                + "（请替换为真实经历）";
    }

    private String coachFollowUp(String section, String labName) {
        return switch (section) {
            case "self-intro" -> "你刚才提到的项目里，哪一部分是你独立完成的？";
            case "role" -> "如果进入" + labName + "，你前三个月打算怎么补齐短板？";
            default -> "如果把刚才的回答压缩到 30 秒，你会保留哪三点？";
        };
    }

    /* ---------------- 简历优化规则 ---------------- */

    private String rewriteSection(String key, String content, List<String> focus) {
        String text = content.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        text = text.replace("我负责了", "负责")
                .replace("我参与了", "参与")
                .replace("我负责", "负责")
                .replace("我参与", "参与")
                .replace("主要是负责", "负责")
                .replace("主要工作是", "")
                .replace("进行了", "完成")
                .replace("做了", "完成")
                .replace("搞了", "实现")
                .replace("弄了", "实现")
                .replace("能够", "可")
                .replace("具有良好的", "具备")
                .replace("有一定的", "具备")
                .replace("非常", "")
                .replace("比较", "");
        if (focus.contains("concise")) {
            text = text.replace("一些", "").replace("相关的工作", "工作").replace("等等", "");
        }

        boolean bullet = Set.of("projects", "internships", "skills", "awards", "other").contains(key);
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            line = line.replaceAll("^[•·\\-\\*]+\\s*", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> pieces = new ArrayList<>();
            if (line.length() > 70 && line.contains("；")) {
                for (String piece : line.split("[；;]")) {
                    String value = piece.trim();
                    if (!value.isEmpty()) {
                        pieces.add(value);
                    }
                }
            } else {
                pieces.add(line);
            }
            for (String piece : pieces) {
                lines.add(bullet ? "• " + piece : piece);
            }
        }
        String after = String.join("\n", lines).trim();
        if (after.isBlank()) {
            return content;
        }
        if (focus.contains("quantify") && Set.of("projects", "internships").contains(key)
                && !after.matches("(?s).*\\d.*")) {
            after += "\n• 结果：（补充可量化指标，如覆盖用户数、准确率、耗时降低比例）";
        }
        return after;
    }

    private List<String> sectionIssues(String key, String content, List<String> keywords, List<String> focus) {
        List<String> issues = new ArrayList<>();
        String text = content == null ? "" : content;
        if (text.length() < 40) {
            issues.add("内容偏短，信息量不足");
        }
        if (focus.contains("quantify") && Set.of("projects", "internships").contains(key)
                && !text.matches("(?s).*\\d.*")) {
            issues.add("缺少可量化结果（规模、比例、耗时）");
        }
        if (text.lines().anyMatch(line -> line.trim().length() > 120)) {
            issues.add("长段落未拆分，扫读成本高");
        }
        if (focus.contains("professional") && text.contains("负责")
                && !text.matches("(?s).*(设计|实现|优化|主导|搭建|重构|排查).*")) {
            issues.add("动词以「负责/参与」为主，建议改成「设计/实现/优化」");
        }
        if (focus.contains("keywords")) {
            int missing = 0;
            for (String keyword : keywords) {
                if (!keyword.isBlank() && !text.toLowerCase().contains(keyword.toLowerCase())) {
                    issues.add("未覆盖目标关键词：" + keyword);
                    if (++missing >= 3) {
                        break;
                    }
                }
            }
        }
        return issues;
    }

    private List<String> sectionReasons(List<String> focus, List<String> issues, String before, String after) {
        List<String> reasons = new ArrayList<>();
        if (focus.contains("concise")) {
            reasons.add("措辞更精炼，去掉口语化与重复表达");
        }
        if (focus.contains("quantify")) {
            reasons.add("对缺少数据的结果给出量化占位提示");
        }
        if (focus.contains("keywords")) {
            reasons.add("围绕目标岗位关键词检查覆盖情况");
        }
        if (focus.contains("professional")) {
            reasons.add("统一为动词开头的要点式表达，提升专业性");
        }
        if (before.equals(after)) {
            reasons.add("原文结构已较完整，仅做了空格与标点清理");
        }
        if (!issues.isEmpty()) {
            reasons.add("针对 " + issues.size() + " 处问题给出改写");
        }
        if (reasons.isEmpty()) {
            reasons.add("按简历常见写法做轻度润色");
        }
        return reasons;
    }

    private int sectionScore(String content, List<String> keywords) {
        int score = 52;
        int length = content == null ? 0 : content.length();
        if (length >= 60) {
            score += 7;
        }
        if (length >= 140) {
            score += 7;
        }
        if (length >= 260) {
            score += 4;
        }
        if (content != null && content.matches("(?s).*\\d.*")) {
            score += 9;
        }
        long bullets = content == null ? 0 : content.lines()
                .filter(line -> line.trim().startsWith("•") || line.trim().startsWith("-"))
                .count();
        if (bullets >= 2) {
            score += 7;
        } else if (content != null && content.lines().count() >= 3) {
            score += 4;
        }
        int hits = 0;
        String lower = content == null ? "" : content.toLowerCase();
        for (String keyword : keywords) {
            if (!keyword.isBlank() && lower.contains(keyword.toLowerCase())) {
                hits++;
            }
        }
        score += Math.min(12, hits * 4);
        if (content != null && content.matches("(?s).*(设计|实现|优化|主导|搭建|重构|排查).*")) {
            score += 4;
        }
        if (length > 700) {
            score -= 6;
        }
        return clamp(score);
    }

    private Map<String, Object> keywordMatches(List<String> texts, List<String> keywords) {
        String joined = String.join("\n", texts).toLowerCase();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword.isBlank()) {
                continue;
            }
            if (joined.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("missing", missing);
        return result;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = item == null ? "" : String.valueOf(item).trim();
                if (!text.isEmpty() && !result.contains(text)) {
                    result.add(text);
                }
            }
        }
        return result;
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
