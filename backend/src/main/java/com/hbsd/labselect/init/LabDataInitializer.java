package com.hbsd.labselect.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实训室 / 导师基础数据初始化。
 *
 * <p>数据源是 classpath:db/labs-tutors-seed.sql（6 个实训室、25 位导师）。脚本本身幂等，
 * 因此每次启动都执行一次：内容变了自动对齐，没变则无副作用；已消耗的名额不会被重置。
 *
 * <p>顺序在 {@link DemoDataInitializer} 之前——演示学生的志愿要按账号挂到真实导师上。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LabDataInitializer implements ApplicationRunner {

    private static final String SEED = "db/labs-tutors-seed.sql";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ensureLabColumns();
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new EncodedResource(new ClassPathResource(SEED), StandardCharsets.UTF_8),
                    false, false,
                    ScriptUtils.DEFAULT_COMMENT_PREFIX, ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER, ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
        } catch (Exception e) {
            // 名册写入失败不应阻断启动（例如并发部署时表被锁），但要留下明确痕迹
            log.error("实训室/导师名册初始化失败：{}", e.getMessage(), e);
            return;
        }
        Integer labs = jdbc.queryForObject("SELECT COUNT(*) FROM labs WHERE status = 1", Integer.class);
        Integer tutors = jdbc.queryForObject("SELECT COUNT(*) FROM tutors WHERE status = 1", Integer.class);
        Integer quota = jdbc.queryForObject("SELECT COALESCE(SUM(quota), 0) FROM tutors WHERE status = 1", Integer.class);
        log.info("实训室名册就绪：{} 个实训室 / {} 位导师 / 总名额 {}", labs, tutors, quota);
    }

    /**
     * 老库补列：schema.sql 用的是 CREATE TABLE IF NOT EXISTS，已存在的表不会自动加列。
     * 这里按 information_schema 比对后增量补齐，升级部署不必手工改表。
     */
    private void ensureLabColumns() {
        List<String> existing = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'labs'",
                String.class);
        Map<String, String> wanted = new LinkedHashMap<>();
        wanted.put("tech_backend", "tech_backend VARCHAR(400) DEFAULT NULL COMMENT '后端/工程技术栈'");
        wanted.put("tech_frontend", "tech_frontend VARCHAR(400) DEFAULT NULL COMMENT '前端/客户端技术栈'");
        wanted.put("ai_direction", "ai_direction VARCHAR(400) DEFAULT NULL COMMENT 'AI/大模型方向'");
        wanted.put("output_types", "output_types VARCHAR(400) DEFAULT NULL COMMENT '项目产出类型'");
        wanted.put("achievements", "achievements VARCHAR(400) DEFAULT NULL COMMENT '竞赛/科研成果'");
        wanted.put("training_mode", "training_mode VARCHAR(400) DEFAULT NULL COMMENT '培养模式'");
        wanted.put("graduation_standard", "graduation_standard VARCHAR(400) DEFAULT NULL COMMENT '考核/结业标准'");
        wanted.put("suitable_for", "suitable_for VARCHAR(400) DEFAULT NULL COMMENT '适合人群'");
        wanted.forEach((col, ddl) -> {
            if (!existing.contains(col)) {
                jdbc.execute("ALTER TABLE labs ADD COLUMN " + ddl);
                log.info("labs 表补列：{}", col);
            }
        });
    }
}
