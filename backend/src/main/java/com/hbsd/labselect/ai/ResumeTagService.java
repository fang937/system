package com.hbsd.labselect.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 简历技能标签抽取。
 *
 * <p>按类目词表从简历正文里匹配技术栈标签，给导师端 AI 初评和学生端「我的简历」提供可读的技能视图。
 * 未配置大模型时这是唯一的标签来源；接入真实模型后仍作为兜底（模型输出失败也能出标签）。</p>
 *
 * <p>匹配规则：长词优先（先命中「Spring Boot」再判「Spring」），命中区间不重复占用，
 * 因此同一段文字不会既打出「Spring Boot」又打出「Spring」。</p>
 */
@Service
public class ResumeTagService {

    /** 单份简历最多返回多少标签，避免长简历刷屏 */
    private static final int MAX_TAGS = 30;

    private static final Map<String, List<String>> DICT = new LinkedHashMap<>();

    static {
        DICT.put("编程语言", List.of(
                "Java", "Python", "C++", "C#", "Go", "JavaScript", "TypeScript",
                "Kotlin", "PHP", "Rust", "Scala", "C语言"));
        DICT.put("后端框架", List.of(
                "Spring Boot", "Spring Cloud", "Spring MVC", "Spring", "MyBatis",
                "Node.js", "Django", "Flask", "FastAPI", "Netty", "Dubbo"));
        DICT.put("前端技术", List.of(
                "Vue", "React", "Angular", "Element UI", "Vite", "Webpack",
                "HTML", "CSS", "Sass", "Tailwind CSS", "Bootstrap",
                "微信小程序", "uni-app", "Axios"));
        DICT.put("数据库", List.of(
                "MySQL", "Redis", "Oracle", "PostgreSQL", "MongoDB",
                "SQL Server", "SQLite", "Elasticsearch"));
        DICT.put("中间件与部署", List.of(
                "Docker", "Kubernetes", "Nginx", "Linux", "Kafka", "RabbitMQ",
                "Git", "Maven", "Jenkins", "CI/CD", "Minio"));
        DICT.put("AI 与数据", List.of(
                "机器学习", "深度学习", "PyTorch", "TensorFlow", "OpenCV",
                "自然语言处理", "计算机视觉", "大模型", "LLM", "RAG", "Agent",
                "LangChain", "Prompt", "Pydantic", "Transformer", "YOLO",
                "知识库", "向量数据库", "微调", "数据分析", "Spark", "Hadoop", "爬虫"));
        DICT.put("移动端", List.of(
                "Android", "iOS", "Flutter", "React Native", "鸿蒙"));
        DICT.put("工程能力", List.of(
                "微服务", "高并发", "分布式", "性能优化", "单元测试",
                "数据结构", "算法", "前后端分离", "RESTful", "接口设计",
                "数据库设计", "需求分析", "项目管理", "敏捷开发"));
    }

    /**
     * 从简历正文抽取标签。
     *
     * @param text 简历正文（可能为空）
     * @return 每个元素形如 {@code {"name":"Spring Boot","category":"后端框架"}}，按词表类目顺序排列
     */
    public List<Map<String, Object>> tags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);

        // 扁平化成「词 -> 类目」，长词优先匹配，避免 Spring 抢在 Spring Boot 前面
        List<String[]> flat = new ArrayList<>();
        DICT.forEach((category, words) -> words.forEach(word -> flat.add(new String[]{word, category})));
        flat.sort((a, b) -> b[0].length() - a[0].length());

        List<int[]> occupied = new ArrayList<>();
        List<String[]> matched = new ArrayList<>();

        for (String[] entry : flat) {
            String needle = entry[0].toLowerCase(Locale.ROOT);
            int index = lower.indexOf(needle);
            int matchLen = needle.length();
            if (index < 0) {
                // 兼容「SpringBoot」「SpringCloud」这类不带空格的写法
                String compact = needle.replace(" ", "");
                if (!compact.equals(needle)) {
                    index = lower.indexOf(compact);
                    matchLen = compact.length();
                }
            }
            if (index < 0) {
                continue;
            }
            int end = index + matchLen;
            boolean overlapped = false;
            for (int[] range : occupied) {
                if (index < range[1] && end > range[0]) {
                    overlapped = true;
                    break;
                }
            }
            if (overlapped) {
                continue;
            }
            occupied.add(new int[]{index, end});
            matched.add(entry);
            if (matched.size() >= MAX_TAGS) {
                break;
            }
        }

        // 按词表类目顺序输出，同类目内保持词表顺序
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : DICT.entrySet()) {
            for (String[] entry : matched) {
                if (!group.getKey().equals(entry[1])) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", entry[0]);
                row.put("category", entry[1]);
                result.add(row);
            }
        }
        return result;
    }

    /** 标签名列表，便于直接拼提示语或写入字符串字段 */
    public List<String> names(String text) {
        return tags(text).stream().map(tag -> String.valueOf(tag.get("name"))).toList();
    }
}
