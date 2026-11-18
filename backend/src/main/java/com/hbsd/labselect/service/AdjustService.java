package com.hbsd.labselect.service;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.entity.AdjustRecord;
import com.hbsd.labselect.mapper.AdjustRecordMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段 7 统筹调剂。
 *
 * 三个优先级严格按顺序执行：
 *   1. 实训室内调剂：学生报过该实训室（任一志愿属于该室）且还有名额；
 *   2. 全院跨实训室调剂：任何还有名额的导师；
 *   3. 人工兜底：由管理员在界面上逐个约谈分配（manual）。
 */
@Service
@RequiredArgsConstructor
public class AdjustService {

    private final JdbcTemplate jdbc;
    private final TutorMapper tutorMapper;
    private final AdjustRecordMapper adjustRecordMapper;
    private final PhaseService phaseService;
    private final NotifyService notifyService;

    /** 调剂池与剩余名额预览。 */
    public Map<String, Object> preview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("students", unmatched());
        result.put("vacancies", vacancies());
        result.put("round", phaseService.currentRound());
        return result;
    }

    /**
     * 调剂池：三轮结束后仍未落实去向的学生。
     *
     * <p>「未录取」的判据是「该生没有任何 final_tutor_id 记录」，而不是只看当前轮次——
     * 只在第 1 轮填报、之后没再填报的学生同样是落榜生，必须进入调剂池（需求：三轮后未录取 → 调剂）。
     * 每个学生只取最近一轮未录取的志愿作为登记行，避免跨轮重复出现。</p>
     */
    public List<Map<String, Object>> unmatched() {
        return jdbc.queryForList("""
                SELECT u.id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.gpa, sp.priority_score AS priorityScore,
                       a.id AS applicationId, a.round_no AS roundNo,
                       a.accept_lab_adjust AS acceptLabAdjust, a.accept_global_adjust AS acceptGlobalAdjust,
                       CASE WHEN a.accept_lab_adjust = 1 OR a.accept_global_adjust = 1 THEN 1 ELSE 0 END AS autoAdjustable,
                       (SELECT GROUP_CONCAT(DISTINCT l.name ORDER BY l.id SEPARATOR ' / ')
                          FROM tutors t JOIN labs l ON l.id = t.lab_id
                         WHERE t.id IN (a.tutor_1_id, a.tutor_2_id, a.tutor_3_id)) AS appliedLabs
                  FROM applications a
                  JOIN users u ON u.id = a.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = a.student_id
                 WHERE a.id = (SELECT a2.id FROM applications a2
                                WHERE a2.student_id = a.student_id AND a2.final_tutor_id IS NULL
                                ORDER BY a2.round_no DESC, a2.id DESC LIMIT 1)
                   AND a.final_tutor_id IS NULL
                   AND NOT EXISTS (SELECT 1 FROM applications m
                                    WHERE m.student_id = a.student_id AND m.final_tutor_id IS NOT NULL)
                   AND NOT EXISTS (SELECT 1 FROM interview_results r
                                    WHERE r.student_id = a.student_id
                                      AND (r.offer_status = 2 OR (r.offer_status = 1 AND r.confirm_status = 0)))
                 ORDER BY sp.priority_score DESC, sp.gpa DESC, u.id
                """);
    }

    public List<Map<String, Object>> vacancies() {
        return jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title, l.id AS labId, l.name AS labName,
                       t.quota, t.remaining_quota AS remainingQuota
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.status = 1 AND t.remaining_quota > 0
                 ORDER BY l.id, t.id
                """);
    }

    /**
     * 执行自动调剂。
     * scope = 1 仅室内；2 室内 + 全院；3 全部（等同 2，保留给后续扩展人工兜底）
     */
    @Transactional
    public Map<String, Object> run(int scope, Long operatorId) {
        phaseService.require(PhaseService.ADJUST);
        List<Map<String, Object>> students = unmatched();
        int labAdjusted = 0;
        int globalAdjusted = 0;
        int manualPending = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> student : students) {
            Long studentId = ((Number) student.get("studentId")).longValue();
            Long applicationId = student.get("applicationId") == null ? null
                    : ((Number) student.get("applicationId")).longValue();
            boolean acceptLab = toBool(student.get("acceptLabAdjust"));
            boolean acceptGlobal = toBool(student.get("acceptGlobalAdjust"));

            // 初选未录取且两项调剂意愿都不服从的学生，不做任何自动调剂，留给管理员人工处理
            if (!acceptLab && !acceptGlobal) {
                manualPending++;
                details.add(student.get("studentName") + " → 不服从调剂，跳过自动调剂，等待管理员处理");
                continue;
            }

            Map<String, Object> target = null;
            int type = 0;
            if (scope >= 1 && acceptLab) {
                target = findVacancy(studentId, true);
                type = 1;
            }
            if (target == null && scope >= 2 && acceptGlobal) {
                target = findVacancy(studentId, false);
                type = 2;
            }
            if (target == null) {
                continue;
            }
            assign(studentId, applicationId, target, type, operatorId,
                    type == 1 ? "阶段7 实训室内自动调剂" : "阶段7 全院自动调剂");
            if (type == 1) {
                labAdjusted++;
            } else {
                globalAdjusted++;
            }
            details.add(student.get("studentName") + " → " + target.get("tutorName")
                    + "（" + target.get("labName") + "，" + (type == 1 ? "室内" : "全院") + "）");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labAdjusted", labAdjusted);
        result.put("globalAdjusted", globalAdjusted);
        result.put("manualPending", manualPending);
        result.put("unmatchedLeft", Math.max(0, students.size() - labAdjusted - globalAdjusted));
        result.put("details", details);
        return result;
    }

    /** 第三优先级：人工兜底分配。 */
    @Transactional
    public void manual(Long studentId, Long tutorId, Long operatorId, String reason) {
        phaseService.require(PhaseService.ADJUST);
        if (studentId == null || tutorId == null) {
            throw new BizException("请选择需要调剂的学生与接收导师");
        }
        Integer studentExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND role = 'STUDENT'", Integer.class, studentId);
        if (studentExists == null || studentExists == 0) {
            throw new BizException("学生不存在");
        }
        Integer matched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE student_id = ? AND final_tutor_id IS NOT NULL",
                Integer.class, studentId);
        if (matched != null && matched > 0) {
            throw new BizException("该学生已被录取");
        }
        Integer inProgress = jdbc.queryForObject("""
                SELECT COUNT(*) FROM interview_results
                 WHERE student_id = ?
                   AND (offer_status = 2 OR (offer_status = 1 AND confirm_status = 0))
                """, Integer.class, studentId);
        if (inProgress != null && inProgress > 0) {
            throw new BizException("该学生仍有待确认的拟录取或递补资格，不能重复安置");
        }
        // 必须显式查出 labId：assign() 要用它写调剂记录的 to_lab_id。
        // 这里曾漏查该列，导致 Map.get("labId") 返回 null，拆箱时抛
        // NullPointerException，被全局异常处理器包装成「系统繁忙」。
        List<Map<String, Object>> tutorRows = jdbc.queryForList("""
                SELECT t.id AS tutorId, t.lab_id AS labId, u.name AS tutorName, l.name AS labName,
                       t.remaining_quota AS remainingQuota, t.status
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.id = ?
                """, tutorId);
        if (tutorRows.isEmpty()) {
            throw new BizException("导师不存在，无法调剂");
        }
        Map<String, Object> tutor = tutorRows.get(0);
        if (!toBool(tutor.get("status"))) {
            throw new BizException("该导师已停止招生，无法调剂");
        }
        // 人工兜底可以跨轮次：取该生最近一条尚未录取的志愿行；确实没有志愿时补一条，
        // 保证「名额扣减 / 调剂记录 / 录取结果」三者指向同一条数据。
        List<Long> applicationIds = jdbc.queryForList("""
                SELECT a.id FROM applications a
                 WHERE a.student_id = ? AND a.final_tutor_id IS NULL
                 ORDER BY a.round_no DESC, a.id DESC LIMIT 1
                """, Long.class, studentId);
        Long applicationId = applicationIds.isEmpty() ? createPlaceholderApplication(studentId)
                : applicationIds.get(0);
        assign(studentId, applicationId, tutor, 3, operatorId,
                reason == null || reason.isBlank() ? "人工约谈强制分配" : reason);
    }

    /**
     * 为从未填报过志愿、但被管理员人工约谈安置的学生补一条志愿行。
     * 没有这条记录，录取结果在「结果公示」里查不到，学生会一直被判定为未录取。
     */
    private Long createPlaceholderApplication(Long studentId) {
        int round = phaseService.currentRound();
        // INSERT IGNORE：重复点击不会因唯一键 (student_id, round_no) 冲突而报 500
        jdbc.update("""
                INSERT IGNORE INTO applications (student_id, round_no, status, submitted_at)
                VALUES (?, ?, 'SUBMITTED', NOW())
                """, studentId, round);
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM applications WHERE student_id = ? AND round_no = ?",
                Long.class, studentId, round);
        if (ids.isEmpty()) {
            throw new BizException("补建志愿记录失败，请重试");
        }
        return ids.get(0);
    }

    private void assign(Long studentId, Long applicationId, Map<String, Object> tutor, int type,
                        Long operatorId, String reason) {
        Long tutorId = requireLong(tutor, "tutorId", "导师");
        Long labId = requireLong(tutor, "labId", "实训室");
        String tutorName = text(tutor.get("tutorName"), "该导师");
        String labName = text(tutor.get("labName"), "实训室");
        Integer remaining = tutor.get("remainingQuota") instanceof Number n ? n.intValue() : null;
        if (remaining != null && remaining <= 0) {
            throw new BizException("导师 " + tutorName + " 名额已满，调剂失败");
        }
        int rows = tutorMapper.takeQuota(tutorId);
        if (rows != 1) {
            throw new BizException("导师 " + tutorName + " 名额已满，调剂失败");
        }
        // 只回写尚未录取的志愿行：条件更新失败说明该生已有录取记录，
        // 必须回滚（含上面扣掉的名额），不能覆盖已录取状态。
        int updated = jdbc.update(
                "UPDATE applications SET final_tutor_id = ?, status = 'MATCHED' "
                        + "WHERE id = ? AND final_tutor_id IS NULL", tutorId, applicationId);
        if (updated != 1) {
            throw new BizException("该学生已有录取记录，调剂失败");
        }
        AdjustRecord record = new AdjustRecord();
        record.setRoundNo(phaseService.currentRound());
        record.setStudentId(studentId);
        record.setToLabId(labId);
        record.setToTutorId(tutorId);
        record.setAdjustType(type);
        record.setStatus(1);
        record.setOperatorId(operatorId);
        record.setReason(reason);
        adjustRecordMapper.insert(record);
        notifyService.send(studentId, "RESULT", "统筹调剂结果",
                "你已被调剂至「" + labName + "」的 " + tutorName
                        + " 导师，请查看录取结果。", "ADJUST", record.getId());
    }

    /** 从 queryForList 的行里取必填的 Long 字段，缺失时给出可读错误而不是 NPE。 */
    private Long requireLong(Map<String, Object> row, String key, String label) {
        Object value = row.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new BizException(label + "数据不完整（缺少 " + key + "），请刷新后重试");
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    /** 在学生填报过的实训室内找还有名额的导师；全局模式下不限制实训室。 */
    private Map<String, Object> findVacancy(Long studentId, boolean sameLabOnly) {
        String sql = sameLabOnly ? """
                SELECT t.id AS tutorId, u.name AS tutorName, l.name AS labName, l.id AS labId,
                       t.remaining_quota AS remainingQuota
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.status = 1 AND t.remaining_quota > 0
                   AND t.lab_id IN (SELECT tt.lab_id FROM applications a
                                      JOIN tutors tt ON tt.id IN (a.tutor_1_id, a.tutor_2_id, a.tutor_3_id)
                                     WHERE a.student_id = ?)
                 ORDER BY t.remaining_quota DESC, t.id LIMIT 1
                """ : """
                SELECT t.id AS tutorId, u.name AS tutorName, l.name AS labName, l.id AS labId,
                       t.remaining_quota AS remainingQuota
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.status = 1 AND t.remaining_quota > 0
                 ORDER BY t.remaining_quota DESC, t.id LIMIT 1
                """;
        List<Map<String, Object>> rows = sameLabOnly
                ? jdbc.queryForList(sql, studentId)
                : jdbc.queryForList(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean toBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() == 1;
        }
        return false;
    }
}
