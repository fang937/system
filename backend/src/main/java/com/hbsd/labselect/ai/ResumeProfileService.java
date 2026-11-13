package com.hbsd.labselect.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简历画像抽取：技术栈之外，再给出「项目与实践经历」「性格与特质」两维信息。
 *
 * <p>目的是让导师在初评面板里不只看一串技术名词，还能快速看到这个人做过什么、是什么样的人。
 * 未配置大模型时用规则实现；接入模型后可作为兜底或与模型输出互相印证。</p>
 *
 * <p>做法：按行/句切分正文 → 动作词 + 项目名词筛出经历句 → 性格词表命中特质 →
 * 最后拼一句「特质 + 代表经历」的摘要。</p>
 */
@Service
@RequiredArgsConstructor
public class ResumeProfileService {

    private final ResumeTagService tagService;

    /** 单份简历最多返回的经历条数 */
    private static final int MAX_EXPERIENCES = 4;
    /** 单条经历展示的最大字数 */
    private static final int EXPERIENCE_MAX_CHARS = 78;
    /** 最多返回的特质数 */
    private static final int MAX_TRAITS = 6;

    /** 动作词：句子里有这些才可能是在讲「做了什么」 */
    private static final List<String> ACTION_WORDS = List.of(
            "负责", "开发", "实现", "设计", "搭建", "完成", "主导", "参与", "牵头",
            "优化", "部署", "维护", "重构", "上线", "编写", "集成", "对接", "担任",
            "组织", "策划", "统筹", "负责", "攻克", "落地", "带队", "管理", "运营");

    /** 项目/成果名词：用来判断这句值不值得当经历 */
    private static final List<String> SUBJECT_WORDS = List.of(
            "项目", "系统", "平台", "模块", "功能", "应用", "网站", "小程序", "APP",
            "竞赛", "比赛", "大赛", "实习", "课题", "论文", "专利", "奖学金", "奖项",
            "团队", "小组", "课题", "产品", "算法", "模型", "服务", "接口", "数据库");

    /** 性格与软素质词表：命中即作为特质展示 */
    private static final List<String> TRAIT_WORDS = List.of(
            "责任心强", "认真负责", "踏实", "细致", "细心", "耐心", "沟通", "团队协作",
            "组织协调", "协调能力", "自学", "自主学习", "主动性", "主动", "执行力",
            "抗压", "逻辑清晰", "逻辑思维", "创新", "创意", "学习能力强", "学习能力",
            "适应能力", "领导力", "统筹", "表达", "亲和力", "严谨", "上进", "钻研",
            "好奇心", "稳定性", "服务意识", "时间管理");

    /** 硬性门槛：太短的分句信息量不足，太长的不适合直接展示 */
    private static final int MIN_EXPERIENCE_CHARS = 12;

    /**
     * 生成简历画像。
     *
     * @return {skills:[{name,category}], experiences:[String], traits:[String], summary:String}
     */
    public Map<String, Object> profile(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", tagService.tags(text));

        List<String> sentences = sentences(text);
        List<String> experiences = experiences(sentences);
        List<String> traits = traits(text);

        result.put("experiences", experiences);
        result.put("traits", traits);
        result.put("summary", summary(traits, experiences));
        return result;
    }

    /** 按句号、分号、换行切句，过滤掉标题式和过短的碎片 */
    private List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String line : text.split("\\R")) {
            String cleaned = line.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            for (String part : cleaned.split("[。！？；;]")) {
                String s = part.trim();
                if (s.length() >= MIN_EXPERIENCE_CHARS) {
                    out.add(s);
                }
            }
            // 整行也留一份，便于抓到「| 项目名 | 角色 | 技术 |」这类表格行
            if (cleaned.length() >= MIN_EXPERIENCE_CHARS && cleaned.length() <= EXPERIENCE_MAX_CHARS * 2) {
                out.add(cleaned);
            }
        }
        return out;
    }

    /** 挑出「做了什么」的句子，去重后按信息量排序取前几条 */
    private List<String> experiences(List<String> sentences) {
        List<String> picked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> projectKeys = new LinkedHashSet<>();

        for (String sentence : sentences) {
            if (!containsAny(sentence, ACTION_WORDS) || !containsAny(sentence, SUBJECT_WORDS)) {
                continue;
            }
            // 纯态度陈述（兴趣、希望、愿意）不算经历，交给「性格与特质」那一维
            if (isAttitudeOnly(sentence)) {
                continue;
            }
            String normalized = sentence.replaceAll("\\s+", "");
            boolean duplicated = false;
            for (String existing : seen) {
                if (existing.contains(normalized) || normalized.contains(existing)) {
                    duplicated = true;
                    break;
                }
            }
            // 同一个项目被不同句子重复描述时，只保留第一条
            String key = projectKey(sentence);
            if (!key.isEmpty() && !projectKeys.add(key)) {
                duplicated = true;
            }
            if (duplicated) {
                continue;
            }
            seen.add(normalized);
            picked.add(truncate(sentence, EXPERIENCE_MAX_CHARS));
            if (picked.size() >= MAX_EXPERIENCES) {
                break;
            }
        }
        return picked;
    }

    /** 只有态度词、没有具体成果的句子，不当经历展示 */
    private boolean isAttitudeOnly(String sentence) {
        if (containsAny(sentence, List.of("项目", "系统", "平台", "竞赛", "比赛", "实习", "课题", "论文", "专利"))) {
            return false;
        }
        return containsAny(sentence, List.of("兴趣", "爱好", "希望", "愿意", "期望", "喜欢", "习惯"));
    }

    /** 取引号/书名号里的项目名，用于同项目去重 */
    private String projectKey(String sentence) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[《“\"]([^》”\"]{2,24})[》”\"]")
                .matcher(sentence);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 性格特质：命中词表并按出现顺序去重 */
    private List<String> traits(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String word : TRAIT_WORDS) {
            if (text.contains(word)) {
                // 「责任心强」命中时不再重复输出「责任心」这类前缀词
                boolean covered = out.stream().anyMatch(existing -> existing.contains(word) || word.contains(existing));
                if (!covered) {
                    out.add(word);
                }
            }
            if (out.size() >= MAX_TRAITS) {
                break;
            }
        }
        return out;
    }

    /** 一句话摘要：性格 + 最有代表性的那段经历 */
    private String summary(List<String> traits, List<String> experiences) {
        StringBuilder sb = new StringBuilder();
        if (!traits.isEmpty()) {
            sb.append(String.join("、", traits.subList(0, Math.min(3, traits.size()))));
        }
        if (!experiences.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("代表经历：").append(truncate(experiences.get(0), 48));
        }
        return sb.toString();
    }

    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int max) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= max) {
            return cleaned;
        }
        // 尽量在标点处断开，避免把「处理库存扣减」这类短语切成两半
        String cut = cleaned.substring(0, max);
        int breakpoint = Math.max(Math.max(cut.lastIndexOf('，'), cut.lastIndexOf('、')), cut.lastIndexOf('；'));
        if (breakpoint > max * 0.55) {
            cut = cut.substring(0, breakpoint);
        }
        return cut + "…";
    }
}
