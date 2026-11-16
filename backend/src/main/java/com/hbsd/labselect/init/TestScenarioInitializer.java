package com.hbsd.labselect.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端测试对象与典型流程场景。
 *
 * <p>与 {@link DemoDataInitializer} 的分工：DemoData 负责“系统能跑起来”的基础数据，
 * 本类负责 20 个可登录的测试学生，以及从「未填报 → 已填报 → 已面试 → 多 Offer → 递补 →
 * 不服从调剂 → 已录取 → 超时/放弃 → 有简历草稿」的典型状态。</p>
 *
 * <p>只写入一次：用 {@code demo.testdata.version} 标记，避免管理员点「重置阶段」后
 * 重启又把旧志愿/Offer 灌回来。测试学生账号本身每次启动都会补齐（按学号判重），
 * 这样重置后仍可用同一批账号重新走流程。</p>
 *
 * <p>版本记录：v1 = 2023901~2023908（8 个）；v2 = 追加 2023909~2023920（12 个），
 * 新增单志愿、三 Offer、超时放弃、主动放弃、递补第 2、两种调剂组合等场景。</p>
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class TestScenarioInitializer implements ApplicationRunner {

    private static final String SEED_FLAG = "demo.testdata.version";
    private static final String VERSION = "2";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /** 学号、姓名、班级、GPA、技能标签 */
    private static final String[][] STUDENTS = {
            {"2023901", "唐子墨", "软工2303", "3.42", "Python,数据分析"},
            {"2023902", "苏念", "软工2303", "3.68", "Java,Spring Boot,MySQL"},
            {"2023903", "顾辰", "软工2303", "3.55", "C++,OpenCV"},
            {"2023904", "沈雨薇", "软工2304", "3.79", "Vue,React,前端工程"},
            {"2023905", "陆星野", "软工2304", "3.33", "Go,微服务,Docker"},
            {"2023906", "许知远", "软工2304", "3.21", "Linux,CTF"},
            {"2023907", "韩清和", "软工2305", "3.88", "Python,机器学习,PyTorch"},
            {"2023908", "叶知秋", "软工2305", "3.75", "Java,Spring Boot,MySQL,Redis"},
            {"2023909", "江予安", "软工2305", "3.51", "Java,软件测试"},
            {"2023910", "程亦然", "软工2305", "3.62", "Python,算法设计"},
            {"2023911", "白景行", "软工2305", "3.84", "C++,嵌入式"},
            {"2023912", "温子瑜", "软工2306", "3.91", "Python,深度学习,CV"},
            {"2023913", "秦书瑶", "软工2306", "3.72", "Vue,Node.js,全栈"},
            {"2023914", "方怀瑾", "软工2306", "3.66", "Java,分布式,消息队列"},
            {"2023915", "邵一诺", "软工2306", "3.44", "SQL,数据仓库"},
            {"2023916", "罗嘉禾", "软工2306", "3.37", "网络安全,渗透测试"},
            {"2023917", "谢云舟", "软工2306", "3.58", "Go,Kubernetes,云原生"},
            {"2023918", "曾若曦", "软工2306", "3.29", "前端,UI 设计"},
            {"2023919", "崔明轩", "软工2306", "3.64", "Vue3,TypeScript,Vite"},
            {"2023920", "蒋雯", "软工2306", "3.47", "测试开发,自动化,Selenium"},
    };

    /**
     * 学号 → 3 位导师登录账号 + 两个调剂开关。
     * 第二、三志愿允许留空串（只填 1 个志愿的边界场景）。
     */
    private static final String[][] APPLICATIONS = {
            {"2023902", "wangyanhua", "liuguanjun", "dingleilei", "1", "1"},
            {"2023903", "wangfengpei", "liujialiang", "changlingling", "1", "1"},
            {"2023904", "zhangxiang", "gaoxuan", "daiyifan", "1", "1"},
            {"2023905", "wanghao", "zhangxiang", "liujialiang", "1", "1"},
            {"2023906", "wangwei", "zhanghuibin", "wangxiaolong", "0", "0"},
            {"2023907", "jiahaitao", "pingyuxuan", "huguoxin", "1", "1"},
            {"2023910", "wangyanhua", "", "", "1", "1"},
            {"2023911", "liuguanjun", "changlingling", "wangwei", "0", "0"},
            {"2023912", "jiahaitao", "pingyuxuan", "wanghao", "1", "1"},
            {"2023913", "zhangxiang", "gaoxuan", "wangpengfan", "1", "1"},
            {"2023914", "yangzhenying", "maqingyu", "dingleilei", "1", "1"},
            {"2023915", "wangwei", "huguoxin", "lihuanzhen", "1", "1"},
            {"2023916", "wangxiaolong", "zhangxiang", "liujialiang", "1", "1"},
            {"2023917", "pingyuxuan", "jiahaitao", "wanghao", "1", "1"},
            {"2023918", "lihuanzhen", "wangwei", "huguoxin", "1", "1"},
            {"2023919", "daiyifan", "pangkaiwen", "yangzhenying", "1", "1"},
            {"2023920", "liutianmeng", "lina", "wanghao", "1", "0"},
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureStudents();
        if (seeded()) {
            return;
        }
        seedApplications();
        seedInterviewResults();
        seedResumeDrafts();
        markSeeded();
        log.info("测试场景就绪：20 个学生测试账号（2023901~2023920，密码 {}）", DemoDataInitializer.DEMO_PASSWORD);
    }

    /* ---------------- 账号与档案 ---------------- */

    private void ensureStudents() {
        for (String[] student : STUDENTS) {
            Long userId = studentId(student[0]);
            if (userId == null) {
                jdbc.update("""
                        INSERT INTO users (username, password, name, role, student_no, status)
                        VALUES (?, ?, ?, 'STUDENT', ?, 1)
                        """, student[0], passwordEncoder.encode(DemoDataInitializer.DEMO_PASSWORD),
                        student[1], student[0]);
                userId = studentId(student[0]);
            }
            if (userId == null) {
                continue;
            }
            Integer profileCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM student_profiles WHERE user_id = ?", Integer.class, userId);
            if (profileCount != null && profileCount > 0) {
                continue;
            }
            BigDecimal gpa = new BigDecimal(student[3]);
            jdbc.update("""
                    INSERT INTO student_profiles (user_id, major, class_name, grade, gpa, skill_tags, priority_score)
                    VALUES (?, '软件工程', ?, '2023', ?, ?, ?)
                    """, userId, student[2], gpa, student[4], gpa.multiply(new BigDecimal("20")));
        }
    }

    /* ---------------- 志愿 ---------------- */

    private void seedApplications() {
        for (String[] item : APPLICATIONS) {
            Long studentId = studentId(item[0]);
            Long t1 = tutorId(item[1]);
            Long t2 = item[2].isBlank() ? null : tutorId(item[2]);
            Long t3 = item[3].isBlank() ? null : tutorId(item[3]);
            boolean missing = studentId == null || t1 == null
                    || (!item[2].isBlank() && t2 == null)
                    || (!item[3].isBlank() && t3 == null);
            if (missing) {
                log.warn("测试场景跳过志愿（学生或导师不存在）：{}", item[0]);
                continue;
            }
            int inserted = jdbc.update("""
                    INSERT INTO applications (student_id, round_no, tutor_1_id, tutor_2_id, tutor_3_id,
                                              accept_lab_adjust, accept_global_adjust, status, submitted_at)
                    SELECT ?, 1, ?, ?, ?, ?, ?, 'SUBMITTED', NOW()
                      FROM DUAL
                     WHERE NOT EXISTS (
                           SELECT 1 FROM applications x WHERE x.student_id = ? AND x.round_no = 1)
                    """, studentId, t1, t2, t3, Integer.parseInt(item[4]), Integer.parseInt(item[5]), studentId);
            if (inserted == 0) {
                continue;
            }
            log.debug("测试志愿已写入：{}", item[0]);
        }
    }

    /* ---------------- 面试与 Offer 场景 ---------------- */

    private void seedInterviewResults() {
        // 已面试、导师尚未处置：用于导师端「拟录取 / 递补 / 淘汰」演示
        seedResult("2023903", "wangfengpei", 86, 0, 0, null);
        // 同一学生收到 2 个拟录取 Offer：用于学生端多 Offer 选择
        seedResult("2023904", "zhangxiang", 88, 1, 0, null);
        seedResult("2023904", "gaoxuan", 84, 1, 0, null);
        // 递补第 1 顺位：用于名额释放后的自动递补
        seedResult("2023905", "wanghao", 78, 2, 0, 1);
        // 已淘汰且不服从任何调剂：用于人工兜底 / 调剂池
        seedResult("2023906", "wangwei", 65, 3, 0, null);
        // 已确认录取：用于结果公示与导出
        seedResult("2023907", "jiahaitao", 90, 1, 1, null);

        // 高分待处置：用于导师端按分数排序、批量处置
        seedResult("2023912", "jiahaitao", 92, 0, 0, null);
        // 同一学生收到 3 个拟录取 Offer：多选极限场景
        seedResult("2023913", "zhangxiang", 89, 1, 0, null);
        seedResult("2023913", "gaoxuan", 87, 1, 0, null);
        seedResult("2023913", "wangpengfan", 85, 1, 0, null);
        // 已确认录取（第二名录取学生）：公示 / 导出多行
        seedResult("2023914", "yangzhenying", 91, 1, 1, null);
        // Offer 超时未确认：学生端显示已超时，名额已释放
        seedResult("2023915", "wangwei", 70, 1, 2, null);
        // 学生主动放弃 Offer：名额已释放
        seedResult("2023916", "wangxiaolong", 74, 1, 3, null);
        // 递补第 2 顺位：验证顺位排序
        seedResult("2023917", "pingyuxuan", 76, 2, 0, 2);
        // 已淘汰但服从双调剂：进入自动调剂池
        seedResult("2023918", "lihuanzhen", 62, 3, 0, null);
        // 已淘汰、仅服从实训室内调剂：验证三级调剂的分支
        seedResult("2023920", "liutianmeng", 58, 3, 0, null);
    }

    /**
     * 写入一条面试结果。只有真正插入时才扣名额，避免重复启动把名额越扣越少。
     *
     * <p>名额口径与真实流程一致：待确认 / 已确认的 Offer 占用名额；
     * 超时放弃、学生主动放弃、淘汰均不占名额。发布时间按状态倒推，保证时间线合理。</p>
     *
     * @param offerStatus 0待处理 1拟录取 2递补 3淘汰
     * @param confirmStatus 0待确认 1已确认 2超时放弃 3学生主动放弃
     */
    private void seedResult(String studentNo, String tutorUsername, int score,
                            int offerStatus, int confirmStatus, Integer reserveRank) {
        Long studentId = studentId(studentNo);
        Long tutorId = tutorId(tutorUsername);
        Long applicationId = applicationId(studentId);
        if (studentId == null || tutorId == null || applicationId == null) {
            log.warn("测试场景跳过面试结果（学生 / 导师 / 志愿缺失）：{} / {}", studentNo, tutorUsername);
            return;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime offerTime = switch (confirmStatus) {
            case 1 -> now.minusDays(1);
            case 2 -> now.minusDays(3);
            case 3 -> now.minusDays(1);
            default -> now;
        };
        LocalDateTime deadline = confirmStatus == 2 ? now.minusDays(1) : offerTime.plusHours(48);
        LocalDateTime confirmTime = switch (confirmStatus) {
            case 1 -> now.minusHours(20);
            case 3 -> now.minusHours(2);
            default -> null;
        };
        Long interviewerId = tutorUserId(tutorId);
        boolean holdQuota = offerStatus == 1 && confirmStatus != 2 && confirmStatus != 3;

        int inserted = jdbc.update("""
                INSERT INTO interview_results
                    (application_id, student_id, tutor_id, round_no, score, score_detail,
                     offer_status, reserve_rank, confirm_status, offer_time, confirm_deadline,
                     confirm_time, interviewer_id, version)
                SELECT ?, ?, ?, 1, ?, '测试场景：面试评分', ?, ?, ?, ?, ?, ?, ?, 0
                  FROM DUAL
                 WHERE NOT EXISTS (
                       SELECT 1 FROM interview_results x
                        WHERE x.application_id = ? AND x.tutor_id = ?)
                """, applicationId, studentId, tutorId, score, offerStatus, reserveRank,
                confirmStatus, offerTime, deadline, confirmTime, interviewerId,
                applicationId, tutorId);
        if (inserted == 0) {
            return;
        }
        if (holdQuota) {
            int rows = jdbc.update("""
                    UPDATE tutors SET remaining_quota = remaining_quota - 1
                     WHERE id = ? AND remaining_quota > 0
                    """, tutorId);
            if (rows != 1) {
                log.warn("测试场景：导师 {} 名额不足，未能锁定名额", tutorUsername);
            }
        }
        if (confirmStatus == 1) {
            jdbc.update("UPDATE applications SET final_tutor_id = ?, status = 'MATCHED' WHERE id = ?",
                    tutorId, applicationId);
        }
    }

    /* ---------------- 简历草稿 ---------------- */

    /** 2023908 后端方向、2023919 前端方向，各预置一份可优化的结构化简历。 */
    private void seedResumeDrafts() {
        seedResumeDraft("2023908", Map.of(
                "targetRole", "后端开发实习生",
                "keywords", List.of("Java", "Spring Boot", "MySQL", "Redis"),
                "sections", List.of(
                        section("basic", "姓名：叶知秋\n学号：2023908\n专业：软件工程\n"
                                + "电话：138****0008\n邮箱：yezhiqiu@example.com\n求职意向：后端开发实习生"),
                        section("education", "河北师范大学 软件工程 2023-2027\nGPA 3.75/4.0\n"
                                + "主修：数据结构、数据库原理、计算机网络、操作系统"),
                        section("projects", "校园二手交易平台（2025.03-2025.06）\n"
                                + "负责后端接口与数据库设计，使用 Spring Boot + MySQL + Redis。\n"
                                + "实现商品发布、搜索、下单与订单状态流转。"),
                        section("internships", "某软件公司 后端开发实习生（2025.07-2025.09）\n"
                                + "参与订单服务重构，补充单元测试与接口文档。"),
                        section("skills", "Java、Spring Boot、MySQL、Redis、Git、Linux"),
                        section("selfIntro", "做事偏工程化，习惯先写清边界再动手；"
                                + "希望在真实项目中补齐高并发与性能优化经验。"))));

        seedResumeDraft("2023919", Map.of(
                "targetRole", "前端开发实习生",
                "keywords", List.of("Vue3", "TypeScript", "Vite"),
                "sections", List.of(
                        section("basic", "姓名：崔明轩\n学号：2023919\n专业：软件工程\n"
                                + "电话：138****0019\n邮箱：cuimingxuan@example.com\n求职意向：前端开发实习生"),
                        section("education", "河北师范大学 软件工程 2023-2027\nGPA 3.64/4.0\n"
                                + "主修：Web 前端开发、人机交互、数据结构、数据库原理"),
                        section("projects", "实验室设备预约系统前端（2025.04-2025.08）\n"
                                + "使用 Vue3 + TypeScript + Vite 搭建管理端，负责路由、权限与表单组件。\n"
                                + "抽象 12 个通用组件，页面重复代码减少约 30%。"),
                        section("internships", "某互联网公司 前端开发实习生（2025.07-2025.10）\n"
                                + "参与活动页开发与首屏性能优化，LCP 从 3.1s 降到 1.8s。"),
                        section("skills", "Vue3、TypeScript、Vite、Element Plus、Git"),
                        section("selfIntro", "偏工程化的前端，重视可维护性与交互细节；"
                                + "希望补齐工程化与性能优化能力。"))));
    }

    private void seedResumeDraft(String studentNo, Map<String, Object> payload) {
        Long studentId = studentId(studentNo);
        if (studentId == null) {
            return;
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM resume_drafts WHERE student_id = ?", Integer.class, studentId);
        if (exists != null && exists > 0) {
            return;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("sections", payload.get("sections"));
        content.put("targetRole", payload.get("targetRole"));
        content.put("keywords", payload.get("keywords"));
        content.put("focus", List.of("concise", "quantify", "keywords", "professional"));

        try {
            jdbc.update("""
                    INSERT INTO resume_drafts (student_id, content, source)
                    VALUES (?, ?, 'manual')
                    ON DUPLICATE KEY UPDATE content = VALUES(content), source = 'manual'
                    """, studentId, objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            log.warn("测试场景简历草稿写入失败（不影响账号使用）：{}", e.getMessage());
        }
    }

    private Map<String, Object> section(String key, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("content", content);
        return row;
    }

    /* ---------------- 查询与标记 ---------------- */

    private Long studentId(String studentNo) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM users WHERE student_no = ? AND role = 'STUDENT'", Long.class, studentNo);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long tutorId(String username) {
        List<Long> ids = jdbc.queryForList("""
                SELECT t.id FROM tutors t JOIN users u ON u.id = t.user_id WHERE u.username = ?
                """, Long.class, username);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long tutorUserId(Long tutorId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT user_id FROM tutors WHERE id = ?", Long.class, tutorId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long applicationId(Long studentId) {
        if (studentId == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM applications WHERE student_id = ? AND round_no = 1", Long.class, studentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean seeded() {
        List<String> values = jdbc.queryForList(
                "SELECT config_value FROM system_config WHERE config_key = ?", String.class, SEED_FLAG);
        return !values.isEmpty() && VERSION.equals(values.get(0));
    }

    private void markSeeded() {
        jdbc.update("""
                INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                VALUES (?, ?, 'STRING', 'DEMO', '测试对象与典型流程场景版本')
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)
                """, SEED_FLAG, VERSION);
    }
}
