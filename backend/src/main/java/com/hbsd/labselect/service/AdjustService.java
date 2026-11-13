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

    public List<Map<String, Object>> unmatched() {
        int round = phaseService.currentRound();
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
                 WHERE a.round_no = ? AND a.final_tutor_id IS NULL
                 ORDER BY sp.priority_score DESC, sp.gpa DESC, u.id
                """, round);
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
        Integer matched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE student_id = ? AND final_tutor_id IS NOT NULL",
                Integer.class, studentId);
        if (matched != null && matched > 0) {
            throw new BizException("该学生已被录取");
        }
        Map<String, Object> tutor = jdbc.queryForMap("""
                SELECT t.id AS tutorId, u.name AS tutorName, l.name AS labName, t.remaining_quota AS remainingQuota
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.id = ?
                """, tutorId);
        Long applicationId = jdbc.queryForObject("""
                SELECT id FROM applications WHERE student_id = ? ORDER BY round_no DESC LIMIT 1
                """, Long.class, studentId);
        assign(studentId, applicationId, tutor, 3, operatorId,
                reason == null || reason.isBlank() ? "人工约谈强制分配" : reason);
    }

    private void assign(Long studentId, Long applicationId, Map<String, Object> tutor, int type,
                        Long operatorId, String reason) {
        Long tutorId = ((Number) tutor.get("tutorId")).longValue();
        int rows = tutorMapper.takeQuota(tutorId);
        if (rows != 1) {
            throw new BizException("导师 " + tutor.get("tutorName") + " 名额已满，调剂失败");
        }
        if (applicationId != null) {
            jdbc.update("UPDATE applications SET final_tutor_id = ?, status = 'MATCHED' WHERE id = ?",
                    tutorId, applicationId);
        }
        AdjustRecord record = new AdjustRecord();
        record.setRoundNo(phaseService.currentRound());
        record.setStudentId(studentId);
        record.setToLabId(((Number) tutor.get("labId")).longValue());
        record.setToTutorId(tutorId);
        record.setAdjustType(type);
        record.setStatus(1);
        record.setOperatorId(operatorId);
        record.setReason(reason);
        adjustRecordMapper.insert(record);
        notifyService.send(studentId, "RESULT", "统筹调剂结果",
                "你已被调剂至「" + tutor.get("labName") + "」的 " + tutor.get("tutorName")
                        + " 导师，请查看录取结果。", "ADJUST", record.getId());
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
