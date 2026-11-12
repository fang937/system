package com.hbsd.labselect.init;

import com.hbsd.labselect.entity.SelectionPhase;
import com.hbsd.labselect.entity.SystemConfig;
import com.hbsd.labselect.entity.User;
import com.hbsd.labselect.mapper.SelectionPhaseMapper;
import com.hbsd.labselect.mapper.SystemConfigMapper;
import com.hbsd.labselect.mapper.UserMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 演示数据：阶段时间轴 + 全局配置 + 8 名演示学生 + 演示志愿。
 *
 * <p>实训室与导师不在这里写 —— 真实名册由 {@link LabDataInitializer} 从
 * db/labs-tutors-seed.sql 灌入，本类只负责"让学生端能跑起来"的那部分。
 *
 * <p>逐项判空，可重复执行：已有数据即跳过，不会重复插入，也不会覆盖业务上已经产生的志愿。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final SelectionPhaseMapper phaseMapper;
    private final SystemConfigMapper configMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    /** 演示学生：学号、姓名、专业、班级、GPA、技能标签 */
    private static final String[][] STUDENTS = {
            {"2023001", "林晓", "计算机科学与技术", "计科2301", "3.82", "Java,算法设计"},
            {"2023002", "陈子昂", "计算机科学与技术", "计科2301", "3.65", "Python,机器学习"},
            {"2023003", "赵雨桐", "软件工程", "软工2302", "3.48", "Vue,前端工程"},
            {"2023004", "孙浩然", "数据科学与大数据技术", "数据2301", "3.71", "Spark,SQL"},
            {"2023005", "周梦琪", "网络工程", "网工2301", "3.36", "Linux,CTF"},
            {"2023006", "吴子涵", "计算机科学与技术", "计科2302", "3.55", "C++,OpenCV"},
            {"2023007", "郑一鸣", "软件工程", "软工2301", "3.28", "Go,微服务"},
            {"2023008", "何静怡", "数据科学与大数据技术", "数据2302", "3.61", "Python,数据分析"},
    };

    /**
     * 演示志愿：学号 → 3 位导师的登录账号。2023001 故意留空，用于演示完整填报流程。
     * 数组末两位是「服从实训室内调剂」「服从全院调剂」。
     */
    private static final String[][] APPLICATIONS = {
            {"2023002", "wangyanhua", "liuguanjun", "dingleilei", "1", "1"},
            {"2023003", "wangfengpei", "liuguanjun", "liujialiang", "1", "1"},
            {"2023004", "zhanghuibin", "wangyanhua", "zhangxiang", "1", "0"},
            {"2023005", "wangxiaolong", "zhangxiang", "wanghao", "1", "1"},
            {"2023006", "wangyanhua", "daiyifan", "gaoxuan", "0", "1"},
            {"2023007", "wangwei", "zhangxiang", "liujialiang", "1", "1"},
            {"2023008", "gaoxuan", "dingleilei", "changlingling", "0", "0"},
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedConfig();
        seedPhases();
        seedStudents();
        seedApplications();
        log.info("演示数据就绪：当前轮次第 {} 轮，活跃阶段 {}", currentRound(), phaseLabel());
    }

    private void seedConfig() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'round.current'", Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        SystemConfig round = new SystemConfig();
        round.setConfigKey("round.current");
        round.setConfigValue("1");
        round.setValueType("INT");
        round.setConfigGroup("ROUND");
        round.setDescription("当前进行中的轮次");
        configMapper.insert(round);

        SystemConfig name = new SystemConfig();
        name.setConfigKey("system.name");
        name.setConfigValue("学生实训室双向选择系统");
        name.setValueType("STRING");
        name.setConfigGroup("SYSTEM");
        name.setDescription("系统名称");
        configMapper.insert(name);
    }

    private void seedPhases() {
        Long count = phaseMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        LocalDate base = LocalDate.now();
        phase("BRIEFING", "宣讲", 1, null, base.minusDays(10), LocalTime.of(8, 0), base.minusDays(7), LocalTime.of(18, 0),
                "导师与实训室介绍，只读阶段");
        phase("OPEN_DAY", "开放日", 2, null, base.minusDays(7), LocalTime.of(8, 0), base.minusDays(4), LocalTime.of(18, 0),
                "线下参观实训室，只读阶段");
        phase("APPLY", "意向填报", 3, 1, base.minusDays(2), LocalTime.of(8, 0), base.plusDays(2), LocalTime.of(22, 0),
                "最多 3 个平行志愿，可勾选是否服从调剂");
        phase("INTERVIEW", "面试与初选", 4, 1, base.plusDays(3), LocalTime.of(8, 0), base.plusDays(6), LocalTime.of(18, 0),
                "导师录入成绩并标记拟录取/递补/淘汰");
        phase("CONFIRM", "拟录取确认", 5, 1, base.plusDays(7), LocalTime.of(8, 0), base.plusDays(8), LocalTime.of(12, 0),
                "学生确认 Offer，超时视为放弃");
        phase("SUPPLEMENT", "分轮补录", 6, null, base.plusDays(9), LocalTime.of(8, 0), base.plusDays(22), LocalTime.of(18, 0),
                "未录满的导师与未录取的学生重新走填报-面试-录取");
        phase("APPLY", "意向填报", 6, 2, base.plusDays(9), LocalTime.of(8, 0), base.plusDays(11), LocalTime.of(22, 0),
                "第 2 轮补录填报");
        phase("INTERVIEW", "面试与初选", 6, 2, base.plusDays(12), LocalTime.of(8, 0), base.plusDays(14), LocalTime.of(18, 0),
                "第 2 轮面试");
        phase("CONFIRM", "拟录取确认", 6, 2, base.plusDays(15), LocalTime.of(8, 0), base.plusDays(16), LocalTime.of(12, 0),
                "第 2 轮确认");
        phase("APPLY", "意向填报", 6, 3, base.plusDays(17), LocalTime.of(8, 0), base.plusDays(18), LocalTime.of(22, 0),
                "第 3 轮补录填报");
        phase("INTERVIEW", "面试与初选", 6, 3, base.plusDays(19), LocalTime.of(8, 0), base.plusDays(20), LocalTime.of(18, 0),
                "第 3 轮面试");
        phase("CONFIRM", "拟录取确认", 6, 3, base.plusDays(21), LocalTime.of(8, 0), base.plusDays(22), LocalTime.of(12, 0),
                "第 3 轮确认");
        phase("ADJUST", "统筹调剂", 7, null, base.plusDays(23), LocalTime.of(8, 0), base.plusDays(25), LocalTime.of(18, 0),
                "室内调剂 → 全院调剂 → 人工兜底");
        phase("PUBLISH", "结果公示", 8, null, base.plusDays(26), LocalTime.of(8, 0), base.plusDays(30), LocalTime.of(18, 0),
                "数据锁定，归档导出");
    }

    private void phase(String code, String name, int seq, Integer round,
                       LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime, String remark) {
        SelectionPhase phase = new SelectionPhase();
        phase.setCode(code);
        phase.setName(name);
        phase.setSeq(seq);
        phase.setRoundNo(round);
        phase.setStartAt(LocalDateTime.of(startDate, startTime));
        phase.setEndAt(LocalDateTime.of(endDate, endTime));
        phase.setStatus("PENDING");
        phase.setRemark(remark);
        phaseMapper.insert(phase);
    }

    private void seedStudents() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'STUDENT'", Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        for (String[] s : STUDENTS) {
            Long userId = seedUser(s[0], s[1], "STUDENT", null, s[0]);
            BigDecimal gpa = new BigDecimal(s[4]);
            jdbc.update("""
                    INSERT INTO student_profiles (user_id, major, class_name, grade, gpa, skill_tags, priority_score)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, userId, s[2], s[3], "2023", gpa, s[5], gpa.multiply(new BigDecimal("20")));
        }
    }

    /**
     * 演示志愿：导师按登录账号查，导师名册没就位时静默跳过（下次启动会补上）。
     */
    private void seedApplications() {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM applications", Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        int inserted = 0;
        for (String[] a : APPLICATIONS) {
            Long t1 = byUsername(a[1]);
            Long t2 = byUsername(a[2]);
            Long t3 = byUsername(a[3]);
            if (t1 == null || t2 == null || t3 == null) {
                log.warn("导师名册未就绪，跳过演示志愿：学号 {}", a[0]);
                continue;
            }
            inserted += jdbc.update("""
                    INSERT INTO applications (student_id, round_no, tutor_1_id, tutor_2_id, tutor_3_id,
                                              accept_lab_adjust, accept_global_adjust, status, submitted_at)
                    SELECT u.id, 1, ?, ?, ?, ?, ?, 'SUBMITTED', NOW() FROM users u
                     WHERE u.student_no = ? AND NOT EXISTS (
                           SELECT 1 FROM applications x WHERE x.student_id = u.id AND x.round_no = 1)
                    """, t1, t2, t3, Integer.parseInt(a[4]), Integer.parseInt(a[5]), a[0]);
        }
        if (inserted > 0) {
            log.info("写入 {} 条演示志愿（学号 2023002~2023008，2023001 留空用于演示填报）", inserted);
        }
    }

    private Long byUsername(String username) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE username = ?", Long.class, username);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long seedUser(String username, String name, String role, Long labId, String studentNo) {
        User user = new User();
        user.setUsername(username);
        user.setName(name);
        user.setRole(role);
        user.setLabId(labId);
        user.setStudentNo(studentNo);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private int currentRound() {
        List<String> v = jdbc.queryForList(
                "SELECT config_value FROM system_config WHERE config_key = 'round.current'", String.class);
        return v.isEmpty() ? 1 : Integer.parseInt(v.get(0));
    }

    private String phaseLabel() {
        return phaseMapper.selectList(null).stream()
                .filter(p -> !LocalDateTime.now().isBefore(p.getStartAt()) && !LocalDateTime.now().isAfter(p.getEndAt()))
                .map(SelectionPhase::getName)
                .findFirst()
                .orElse("无");
    }
}
