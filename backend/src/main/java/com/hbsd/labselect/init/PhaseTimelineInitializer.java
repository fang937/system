package com.hbsd.labselect.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 把「分轮补录」改造为三轮可折叠的录取批次。
 *
 * <p>阶段六原有模型是一条 {@code SUPPLEMENT / round_no = null} 的全局补录阶段。
 * 现在按轮次拆成三条：第一轮录取、第二轮录取、第三轮录取，每条的时间窗是该轮
 * 「面试与初选 → 拟录取确认」两段的包络；志愿填报单独成段，不纳入录取窗口。</p>
 *
 * <p>迁移分两步且幂等：</p>
 * <ol>
 *   <li>结构性步骤（只跑一次，由 {@code phase.supplement.model} 标记）：补齐三条记录、
 *       改名、并按新口径重算时间窗；</li>
 *   <li>每次启动都执行：只校正名称，不动管理员已经调整过的时间窗。</li>
 * </ol>
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class PhaseTimelineInitializer implements ApplicationRunner {

    private static final String[] BATCH_NAMES = {"第一轮录取", "第二轮录取", "第三轮录取"};
    private static final String BATCH_REMARK = "本轮录取总窗口，覆盖面试与拟录取确认";
    private static final List<String> ACCEPT_CODES = List.of("INTERVIEW", "CONFIRM");
    private static final String MODEL_FLAG = "phase.supplement.model";
    private static final String MODEL_VERSION = "accept-v2";

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrate();
    }

    private void migrate() {
        List<Map<String, Object>> batches = jdbc.queryForList(
                "SELECT id, round_no, name FROM selection_phases WHERE code = 'SUPPLEMENT' ORDER BY id");
        boolean reshaped = MODEL_VERSION.equals(configValue(MODEL_FLAG));

        Map<String, Object> global = null;
        for (Map<String, Object> row : batches) {
            if (row.get("round_no") == null) {
                global = row;
                break;
            }
        }

        for (int round = 1; round <= BATCH_NAMES.length; round++) {
            Map<String, Object> envelope = envelope(round);
            if (envelope == null) {
                continue;
            }
            LocalDateTime start = toTime(envelope.get("start_at"));
            LocalDateTime end = toTime(envelope.get("end_at"));

            Map<String, Object> existing = null;
            for (Map<String, Object> row : batches) {
                Integer batchRound = toRound(row.get("round_no"));
                if (batchRound != null && batchRound == round) {
                    existing = row;
                    break;
                }
            }

            if (existing != null) {
                if (reshaped) {
                    jdbc.update("UPDATE selection_phases SET name = ? WHERE id = ?",
                            BATCH_NAMES[round - 1], existing.get("id"));
                } else {
                    jdbc.update("""
                            UPDATE selection_phases SET name = ?, round_no = ?, start_at = ?, end_at = ?, remark = ?
                             WHERE id = ?
                            """, BATCH_NAMES[round - 1], round, Timestamp.valueOf(start), Timestamp.valueOf(end),
                            BATCH_REMARK, existing.get("id"));
                }
            } else if (round == 1 && global != null) {
                // 复用原来的全局行，保留其 id
                jdbc.update("""
                        UPDATE selection_phases SET name = ?, round_no = ?, start_at = ?, end_at = ?, remark = ?
                         WHERE id = ?
                        """, BATCH_NAMES[round - 1], round, Timestamp.valueOf(start), Timestamp.valueOf(end),
                        BATCH_REMARK, global.get("id"));
                global = null;
            } else {
                jdbc.update("""
                        INSERT INTO selection_phases (code, name, seq, round_no, start_at, end_at, status, remark)
                        VALUES ('SUPPLEMENT', ?, 6, ?, ?, ?, 'PENDING', ?)
                        """, BATCH_NAMES[round - 1], round,
                        Timestamp.valueOf(start), Timestamp.valueOf(end), BATCH_REMARK);
            }
        }

        renumber();
        if (!reshaped) {
            writeConfig(MODEL_FLAG, MODEL_VERSION);
            log.info("阶段模型已升级：分轮补录改为「第一轮/第二轮/第三轮录取」，窗口取面试与确认的包络");
        }
    }

    /** 某轮「面试与初选 + 拟录取确认」的时间包络。 */
    private Map<String, Object> envelope(int round) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT MIN(start_at) AS start_at, MAX(end_at) AS end_at
                  FROM selection_phases
                 WHERE round_no = ? AND code IN ('INTERVIEW', 'CONFIRM')
                """, round);
        return row.get("start_at") == null ? null : row;
    }

    /**
     * 重排阶段序号，严格对齐流程图：
     * 1 宣讲、2 开放日、3 意向填报、4 面试与初选、5 拟录取确认、6 分轮录取、7 统筹调剂、8 结果公示。
     * 同一环节跨轮次共用同一个序号——三轮填报都是 3，三轮面试都是 4，依此类推。
     */
    private void renumber() {
        jdbc.update("UPDATE selection_phases SET seq = 1 WHERE code = 'BRIEFING'");
        jdbc.update("UPDATE selection_phases SET seq = 2 WHERE code = 'OPEN_DAY'");
        jdbc.update("UPDATE selection_phases SET seq = 3 WHERE code = 'APPLY'");
        jdbc.update("UPDATE selection_phases SET seq = 4 WHERE code = 'INTERVIEW'");
        jdbc.update("UPDATE selection_phases SET seq = 5 WHERE code = 'CONFIRM'");
        jdbc.update("UPDATE selection_phases SET seq = 6 WHERE code = 'SUPPLEMENT'");
        jdbc.update("UPDATE selection_phases SET seq = 7 WHERE code = 'ADJUST'");
        jdbc.update("UPDATE selection_phases SET seq = 8 WHERE code = 'PUBLISH'");
    }

    private String configValue(String key) {
        List<String> values = jdbc.queryForList(
                "SELECT config_value FROM system_config WHERE config_key = ?", String.class, key);
        return values.isEmpty() ? null : values.get(0);
    }

    private void writeConfig(String key, String value) {
        jdbc.update("""
                INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                VALUES (?, ?, 'STRING', 'PHASE', '阶段模型版本标记')
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)
                """, key, value);
    }

    private Integer toRound(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private LocalDateTime toTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        return null;
    }
}
