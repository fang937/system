package com.hbsd.labselect.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 演示用学生名册：按 402 实训室 PBL 名单（25-402-pbl.xlsx）统一命名，并补齐到 38 人。
 *
 * <p>规则：按学号顺序套用 {@link #NAMES}；再新建 10 个账号（2023921~2023930）接续名单。
 * 整体只执行一次，由 {@code demo.roster.version} 标记，避免每次启动重复改名。</p>
 *
 * <p>版本记录：v1 = 前 14 人用给定姓名、其余「灭01」~「灭24」；
 * v2 = 登录页扩到 16 人，前 16 人固定命名，其余顺延为「灭01」~「灭22」；
 * v3 = 全员改为 402 花名册真实姓名。</p>
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class StudentRosterInitializer implements ApplicationRunner {

    private static final String FLAG = "demo.roster.version";
    private static final String VERSION = "v3";

    /**
     * 402 实训室学生名单（来自 25-402-pbl.xlsx，按学号顺序）。
     * 前 16 个与 {@code Login.vue} 的 demoAccounts.STUDENT 一一对应，改名时两处必须同步。
     */
    private static final String[] NAMES = {
            "杨泽萱", "李佳欣", "庞浩宇", "方博文", "吕佳凝", "王思棋", "安家宜", "张增",
            "赵鹏宇", "张艺檬", "郭佳欢", "董哲宇", "俞鹤鸣", "闫宇星", "郑佳蕊", "吕鑫",
            "田野", "邢烘昊", "王雨昕", "秦浩然", "荀皓", "侯利博", "张初晨", "聂孝翰",
            "赖信廷", "高铭春", "刘瀚璇", "李宇彤", "肖德威", "黄志轩", "邱智勇", "王世恒",
            "刘和平", "周文涛", "石凯程", "赵一聪", "胡菲", "孟庆巍"
    };

    /** 新增账号的学号。 */
    private static final String[] NEW_NOS = {
            "2023921", "2023922", "2023923", "2023924", "2023925",
            "2023926", "2023927", "2023928", "2023929", "2023930"
    };

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> flag = jdbc.queryForList(
                "SELECT config_value FROM system_config WHERE config_key = ?", String.class, FLAG);
        if (!flag.isEmpty() && VERSION.equals(flag.get(0))) {
            return;
        }

        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT id, student_no FROM users WHERE role = 'STUDENT' ORDER BY student_no");
        int renamed = 0;
        for (int i = 0; i < students.size(); i++) {
            String name = i < NAMES.length ? NAMES[i] : "学生" + (i + 1);
            jdbc.update("UPDATE users SET name = ? WHERE id = ?", name, students.get(i).get("id"));
            renamed++;
        }

        // 新账号沿用现有学生的密码哈希，避免与历史数据不一致
        List<String> hashes = jdbc.queryForList(
                "SELECT password FROM users WHERE role = 'STUDENT' ORDER BY student_no LIMIT 1", String.class);
        String passwordHash = hashes.isEmpty() || hashes.get(0) == null
                ? passwordEncoder.encode(DemoDataInitializer.DEMO_PASSWORD)
                : hashes.get(0);

        int created = 0;
        for (int i = 0; i < NEW_NOS.length; i++) {
            String no = NEW_NOS[i];
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE student_no = ? AND role = 'STUDENT'",
                    Integer.class, no);
            if (exists != null && exists > 0) {
                continue;
            }
            int nameIndex = Math.min(NAMES.length - 1, NAMES.length - NEW_NOS.length + i);
            String name = NAMES[nameIndex];
            jdbc.update("""
                    INSERT INTO users (username, password, name, role, student_no, status)
                    VALUES (?, ?, ?, 'STUDENT', ?, 1)
                    """, no, passwordHash, name, no);
            Long userId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE student_no = ? AND role = 'STUDENT'", Long.class, no);
            BigDecimal gpa = new BigDecimal("3.20").add(new BigDecimal("0.06").multiply(new BigDecimal(i)));
            jdbc.update("""
                    INSERT INTO student_profiles (user_id, major, class_name, grade, gpa, skill_tags, priority_score)
                    VALUES (?, '软件工程', '软工2307', '2023', ?, 'Java,Spring Boot,MySQL', ?)
                    """, userId, gpa, gpa.multiply(new BigDecimal("20")));
            created++;
        }

        jdbc.update("""
                INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                VALUES (?, ?, 'STRING', 'DEMO', '演示学生名册版本')
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)
                """, FLAG, VERSION);
        log.info("演示学生名册就绪：改名 {} 人，新增 {} 人（密码与现有学生一致）", renamed, created);
    }
}
