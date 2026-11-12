package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.ai.AiService;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.dto.AiScoreReq;
import com.hbsd.labselect.dto.ScheduleReq;
import com.hbsd.labselect.entity.AdjustRecord;
import com.hbsd.labselect.entity.InterviewResult;
import com.hbsd.labselect.entity.InterviewSchedule;
import com.hbsd.labselect.entity.Tutor;
import com.hbsd.labselect.mapper.AdjustRecordMapper;
import com.hbsd.labselect.mapper.InterviewResultMapper;
import com.hbsd.labselect.mapper.InterviewScheduleMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导师 + 实训室管理员合并工作台。
 *
 * 同一个工作台里两套能力：
 *   TUTOR_WORK  —— 报考我的学生、面试打分、拟录取/递补/淘汰
 *   LAB_MANAGE  —— 本室面试排期、本室招生进度、室内调剂
 * 权限由账号 role 决定，前端按 caps 渲染菜单，后端在此二次校验，越权直接 403。
 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final JdbcTemplate jdbc;
    private final TutorMapper tutorMapper;
    private final InterviewResultMapper resultMapper;
    private final InterviewScheduleMapper scheduleMapper;
    private final AdjustRecordMapper adjustRecordMapper;
    private final PhaseService phaseService;
    private final OfferService offerService;
    private final NotifyService notifyService;
    private final AiService aiService;

    private Long tutorId() {
        UserContext.CurrentUser user = UserContext.get();
        if (user.tutorId() == null) {
            throw BizException.forbidden("当前账号没有导师身份，无法使用导师事务");
        }
        return user.tutorId();
    }

    private Long labId() {
        UserContext.CurrentUser user = UserContext.get();
        if (user.labId() == null) {
            throw BizException.forbidden("当前账号未绑定实训室，无法使用实训室管理功能");
        }
        return user.labId();
    }

    /** 工作台顶部概览：本人名额、待处理人数等信息。 */
    public Map<String, Object> overview() {
        UserContext.CurrentUser user = UserContext.get();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phase", phaseService.now());
        map.put("role", user.role());
        map.put("labId", user.labId());
        if (user.labId() != null) {
            List<Map<String, Object>> labRows = jdbc.queryForList(
                    "SELECT id, name, location, description FROM labs WHERE id = ?", user.labId());
            map.put("lab", labRows.isEmpty() ? null : labRows.get(0));
        }
        if (user.tutorId() != null) {
            int round = phaseService.currentRound();
            Tutor tutor = tutorMapper.selectById(user.tutorId());
            map.put("quota", tutor.getQuota());
            map.put("remainingQuota", tutor.getRemainingQuota());
            map.put("applicantCount", jdbc.queryForObject("""
                    SELECT COUNT(*) FROM applications a
                     WHERE a.round_no = ? AND (a.tutor_1_id = ? OR a.tutor_2_id = ? OR a.tutor_3_id = ?)
                    """, Integer.class, round, user.tutorId(), user.tutorId(), user.tutorId()));
            map.put("pendingCount", jdbc.queryForObject("""
                    SELECT COUNT(*) FROM interview_results r
                     WHERE r.tutor_id = ? AND r.round_no = ? AND r.offer_status = 0
                    """, Integer.class, user.tutorId(), round));
        }
        if (user.labId() != null) {
            int round = phaseService.currentRound();
            map.put("labProgress", labSummary(user.labId(), round));
        }
        return map;
    }

    /** 报考我的学生名单（按三个平行志愿定位到本人）。 */
    public List<Map<String, Object>> applicants(Integer roundNo) {
        Long tutorId = tutorId();
        int round = roundNo == null ? phaseService.currentRound() : roundNo;
        return jdbc.queryForList("""
                SELECT a.id                          AS applicationId,
                       a.round_no                    AS roundNo,
                       u.id                          AS studentId,
                       u.name                        AS studentName,
                       u.student_no                  AS studentNo,
                       sp.major, sp.class_name       AS className, sp.grade, sp.gpa,
                       sp.skill_tags                 AS skillTags,
                       sp.priority_score             AS priorityScore,
                       sr.file_name                  AS resumeName,
                       sr.uploaded_at                AS resumeUploadedAt,
                       CASE WHEN a.tutor_1_id = ? THEN 1 WHEN a.tutor_2_id = ? THEN 2 ELSE 3 END AS choiceIndex,
                       r.id                          AS resultId,
                       r.score, r.score_detail       AS scoreDetail,
                       r.offer_status                AS offerStatus,
                       r.reserve_rank                AS reserveRank,
                       r.confirm_status              AS confirmStatus,
                       r.confirm_deadline            AS confirmDeadline,
                       r.interview_remark            AS interviewRemark
                  FROM applications a
                  JOIN users u             ON u.id = a.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = a.student_id
                  LEFT JOIN student_resumes sr ON sr.student_id = a.student_id
                  LEFT JOIN interview_results r ON r.application_id = a.id AND r.tutor_id = ?
                 WHERE a.round_no = ?
                   AND (a.tutor_1_id = ? OR a.tutor_2_id = ? OR a.tutor_3_id = ?)
                 ORDER BY COALESCE(r.score, -1) DESC, sp.gpa DESC, a.id
                """, tutorId, tutorId, tutorId, round, tutorId, tutorId, tutorId);
    }

    /** 阶段 4：录入/修改面试成绩。 */
    @Transactional
    public void saveScore(Long applicationId, java.math.BigDecimal score, String scoreDetail, String remark) {
        Long tutorId = tutorId();
        phaseService.require(PhaseService.INTERVIEW);
        Long studentId = jdbc.queryForObject(
                "SELECT student_id FROM applications WHERE id = ?", Long.class, applicationId);
        if (studentId == null) {
            throw new BizException("志愿记录不存在");
        }
        InterviewResult result = findOrCreate(applicationId, studentId, tutorId);
        result.setScore(score);
        result.setScoreDetail(scoreDetail);
        if (remark != null && !remark.isBlank()) {
            result.setInterviewRemark(remark);
        }
        result.setInterviewerId(UserContext.get().id());
        result.setVersion(result.getVersion() + 1);
        resultMapper.updateById(result);
    }

    /**
     * 多维评分（AI 参考分落地）：按 score_rubrics 的权重折算总分写入 interview_results.score，
     * 各维度明细写入 interview_scores，便于事后复盘与口径统一。
     */
    @Transactional
    public void saveScores(Long applicationId, List<AiScoreReq.Item> items) {
        Long tutorId = tutorId();
        phaseService.require(PhaseService.INTERVIEW);
        if (items == null || items.isEmpty()) {
            throw new BizException("缺少评分维度");
        }
        Long studentId = jdbc.queryForObject(
                "SELECT student_id FROM applications WHERE id = ?", Long.class, applicationId);
        if (studentId == null) {
            throw new BizException("志愿记录不存在");
        }

        List<Map<String, Object>> rubric = aiService.rubric();
        Map<String, Integer> weights = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Map<String, Object> row : rubric) {
            String code = String.valueOf(row.get("code"));
            weights.put(code, ((Number) row.get("weight")).intValue());
            names.put(code, String.valueOf(row.get("name")));
        }
        if (weights.isEmpty()) {
            throw new BizException("评分标准尚未配置");
        }

        double weighted = 0;
        int weightSum = 0;
        List<String> detail = new ArrayList<>();
        for (AiScoreReq.Item item : items) {
            if (item == null || item.code() == null || !weights.containsKey(item.code()) || item.score() == null) {
                continue;
            }
            double score = item.score().doubleValue();
            if (score < 0 || score > 100) {
                throw new BizException("「" + names.get(item.code()) + "」分数需在 0-100 之间");
            }
            int weight = weights.get(item.code());
            weighted += score * weight;
            weightSum += weight;
            jdbc.update("""
                    INSERT INTO interview_scores (application_id, dimension_code, score, comment)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE score = VALUES(score), comment = VALUES(comment)
                    """, applicationId, item.code(), item.score(), item.comment());
            detail.add(names.get(item.code()) + " " + trimScore(score));
        }
        if (weightSum == 0) {
            throw new BizException("没有有效的评分维度");
        }

        double total = Math.round(weighted / weightSum * 10) / 10.0;
        InterviewResult result = findOrCreate(applicationId, studentId, tutorId);
        result.setScore(BigDecimal.valueOf(total));
        String text = "维度分：" + String.join(" / ", detail) + "（加权 " + total + "）";
        result.setScoreDetail(text.length() > 250 ? text.substring(0, 250) : text);
        result.setInterviewerId(UserContext.get().id());
        result.setVersion(result.getVersion() + 1);
        resultMapper.updateById(result);
    }

    private String trimScore(double score) {
        return score == Math.rint(score)
                ? String.valueOf((long) score)
                : String.valueOf(Math.round(score * 10) / 10.0);
    }

    /** 阶段 4：拟录取 / 递补 / 淘汰。 */    @Transactional
    public void decide(Long applicationId, int offerStatus, String remark) {
        Long tutorId = tutorId();
        phaseService.require(PhaseService.INTERVIEW);
        Long studentId = jdbc.queryForObject(
                "SELECT student_id FROM applications WHERE id = ?", Long.class, applicationId);
        if (studentId == null) {
            throw new BizException("志愿记录不存在");
        }
        InterviewResult result = findOrCreate(applicationId, studentId, tutorId);
        offerService.decide(result.getId(), offerStatus, tutorId, remark);
    }

    /** 我发出的 Offer 及学生确认状态。 */
    public List<Map<String, Object>> myOffers(Integer roundNo) {
        Long tutorId = tutorId();
        int round = roundNo == null ? phaseService.currentRound() : roundNo;
        return jdbc.queryForList("""
                SELECT r.id, r.application_id AS applicationId, r.round_no AS roundNo,
                       u.id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.gpa,
                       r.score, r.offer_status AS offerStatus, r.reserve_rank AS reserveRank,
                       r.confirm_status AS confirmStatus, r.offer_time AS offerTime,
                       r.confirm_deadline AS confirmDeadline, r.confirm_time AS confirmTime
                  FROM interview_results r
                  JOIN users u ON u.id = r.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = r.student_id
                 WHERE r.tutor_id = ? AND r.round_no = ? AND r.offer_status IN (1, 2, 3)
                 ORDER BY r.offer_status, r.reserve_rank, r.id
                """, tutorId, round);
    }

    /* ---------------- 实训室管理能力（LAB_MANAGE） ---------------- */

    public List<Map<String, Object>> schedules(Integer roundNo) {
        Long labId = labId();
        int round = roundNo == null ? phaseService.currentRound() : roundNo;
        return jdbc.queryForList("""
                SELECT s.id, s.lab_id AS labId, s.round_no AS roundNo, s.tutor_id AS tutorId,
                       u.name AS tutorName, s.interview_date AS interviewDate,
                       s.start_time AS startTime, s.end_time AS endTime,
                       s.location, s.capacity, s.status
                  FROM interview_schedules s
                  LEFT JOIN tutors t ON t.id = s.tutor_id
                  LEFT JOIN users u  ON u.id = t.user_id
                 WHERE s.lab_id = ? AND s.round_no = ?
                 ORDER BY s.interview_date, s.start_time
                """, labId, round);
    }

    @Transactional
    public void saveSchedule(Long id, ScheduleReq req) {
        Long labId = labId();
        phaseService.require(PhaseService.INTERVIEW);
        if (req.interviewDate() == null) {
            throw new BizException("请选择面试日期");
        }
        if (req.tutorId() != null) {
            Tutor tutor = tutorMapper.selectById(req.tutorId());
            if (tutor == null || !labId.equals(tutor.getLabId())) {
                throw new BizException("只能为本实训室导师排期");
            }
        }
        InterviewSchedule schedule = id == null ? new InterviewSchedule() : scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BizException("排期记录不存在");
        }
        if (id != null && !labId.equals(schedule.getLabId())) {
            throw BizException.forbidden("不能修改其他实训室的排期");
        }
        if (id == null) {
            schedule.setLabId(labId);
            schedule.setRoundNo(phaseService.currentRound());
            schedule.setStatus(1);
            schedule.setCreatedBy(UserContext.get().id());
        }
        schedule.setTutorId(req.tutorId());
        schedule.setInterviewDate(req.interviewDate());
        schedule.setStartTime(req.startTime());
        schedule.setEndTime(req.endTime());
        schedule.setLocation(req.location());
        schedule.setCapacity(req.capacity());
        if (id == null) {
            scheduleMapper.insert(schedule);
        } else {
            scheduleMapper.updateById(schedule);
        }
    }

    @Transactional
    public void deleteSchedule(Long id) {
        Long labId = labId();
        InterviewSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return;
        }
        if (!labId.equals(schedule.getLabId())) {
            throw BizException.forbidden("不能删除其他实训室的排期");
        }
        scheduleMapper.deleteById(id);
    }

    public Map<String, Object> labProgress(Integer roundNo) {
        Long labId = labId();
        int round = roundNo == null ? phaseService.currentRound() : roundNo;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("round", round);
        List<Map<String, Object>> tutors = jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title, t.quota,
                       t.remaining_quota AS remainingQuota,
                       (SELECT COUNT(*) FROM applications a WHERE a.round_no = ?
                          AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id)) AS applicantCount,
                       (SELECT COUNT(*) FROM interview_results r
                         WHERE r.tutor_id = t.id AND r.round_no = ? AND r.offer_status = 1) AS offerCount,
                       (SELECT COUNT(*) FROM interview_results r
                         WHERE r.tutor_id = t.id AND r.round_no = ? AND r.offer_status = 1
                           AND r.confirm_status = 1) AS confirmedCount,
                       (SELECT COUNT(*) FROM interview_results r
                         WHERE r.tutor_id = t.id AND r.round_no = ? AND r.offer_status = 2) AS reserveCount,
                       (SELECT COUNT(*) FROM interview_results r
                         WHERE r.tutor_id = t.id AND r.round_no = ? AND r.offer_status = 0) AS pendingCount
                  FROM tutors t JOIN users u ON u.id = t.user_id
                 WHERE t.lab_id = ? AND t.status = 1
                 ORDER BY t.id
                """, round, round, round, round, round, labId);
        result.put("tutors", tutors);
        result.put("summary", labSummary(labId, round));
        return result;
    }

    private Map<String, Object> labSummary(Long labId, int round) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(t.quota), 0)           AS totalQuota,
                       COALESCE(SUM(t.remaining_quota), 0) AS remainingQuota,
                       COALESCE(SUM(t.quota - t.remaining_quota), 0) AS lockedQuota,
                       COUNT(t.id)                         AS tutorCount,
                       (SELECT COUNT(*) FROM interview_results r JOIN tutors t2 ON t2.id = r.tutor_id
                         WHERE t2.lab_id = ? AND r.round_no = ? AND r.offer_status = 1) AS offerCount,
                       (SELECT COUNT(*) FROM interview_results r JOIN tutors t2 ON t2.id = r.tutor_id
                         WHERE t2.lab_id = ? AND r.round_no = ? AND r.offer_status = 1 AND r.confirm_status = 1) AS confirmedCount
                  FROM tutors t WHERE t.lab_id = ? AND t.status = 1
                """, labId, round, labId, round, labId);
    }

    /** 本室学生去向（已发 Offer / 已确认 / 递补）。 */
    public List<Map<String, Object>> labStudents(Integer roundNo) {
        Long labId = labId();
        int round = roundNo == null ? phaseService.currentRound() : roundNo;
        return jdbc.queryForList("""
                SELECT u.id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.gpa,
                       tu.name AS tutorName, t.title,
                       r.score, r.offer_status AS offerStatus, r.confirm_status AS confirmStatus,
                       r.reserve_rank AS reserveRank
                  FROM interview_results r
                  JOIN tutors t ON t.id = r.tutor_id
                  JOIN users tu ON tu.id = t.user_id
                  JOIN users u  ON u.id = r.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = r.student_id
                 WHERE t.lab_id = ? AND r.round_no = ? AND r.offer_status IN (1, 2, 3)
                 ORDER BY r.offer_status, r.confirm_status, r.score DESC
                """, labId, round);
    }

    /** 室内调剂候选：报过本室但至今未被任何导师录取的学生。 */
    public List<Map<String, Object>> labPool() {
        Long labId = labId();
        return jdbc.queryForList("""
                SELECT u.id AS studentId, u.name AS studentName, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.gpa, sp.priority_score AS priorityScore,
                       a.id AS applicationId, a.round_no AS roundNo,
                       a.accept_lab_adjust AS acceptLabAdjust, a.accept_global_adjust AS acceptGlobalAdjust
                  FROM applications a
                  JOIN users u ON u.id = a.student_id
                  LEFT JOIN student_profiles sp ON sp.user_id = a.student_id
                 WHERE a.final_tutor_id IS NULL
                   AND EXISTS (SELECT 1 FROM tutors t WHERE t.lab_id = ?
                                AND (t.id = a.tutor_1_id OR t.id = a.tutor_2_id OR t.id = a.tutor_3_id))
                 ORDER BY sp.priority_score DESC, sp.gpa DESC
                """, labId);
    }

    /** 本室可接收调剂的导师（还有剩余名额）。 */
    public List<Map<String, Object>> labVacancies() {
        Long labId = labId();
        return jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title, t.quota,
                       t.remaining_quota AS remainingQuota
                  FROM tutors t JOIN users u ON u.id = t.user_id
                 WHERE t.lab_id = ? AND t.status = 1 AND t.remaining_quota > 0
                 ORDER BY t.remaining_quota DESC
                """, labId);
    }

    /** 阶段 7 第一优先级：实训室内调剂。 */
    @Transactional
    public void labAdjust(Long studentId, Long tutorId, String reason) {
        Long labId = labId();
        phaseService.require(PhaseService.ADJUST);
        Tutor tutor = tutorMapper.selectById(tutorId);
        if (tutor == null || !labId.equals(tutor.getLabId())) {
            throw new BizException("只能调剂到本实训室的导师");
        }
        Integer matched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE student_id = ? AND final_tutor_id IS NOT NULL",
                Integer.class, studentId);
        if (matched != null && matched > 0) {
            throw new BizException("该学生已被录取，无需调剂");
        }
        int rows = tutorMapper.takeQuota(tutorId);
        if (rows != 1) {
            throw new BizException("该导师名额已满");
        }
        Long applicationId = jdbc.queryForObject("""
                SELECT a.id FROM applications a
                 WHERE a.student_id = ? AND a.final_tutor_id IS NULL
                 ORDER BY a.round_no DESC LIMIT 1
                """, Long.class, studentId);
        jdbc.update("UPDATE applications SET final_tutor_id = ?, status = 'MATCHED' WHERE id = ?",
                tutorId, applicationId);

        AdjustRecord record = new AdjustRecord();
        record.setRoundNo(phaseService.currentRound());
        record.setStudentId(studentId);
        record.setToLabId(labId);
        record.setToTutorId(tutorId);
        record.setAdjustType(1);
        record.setStatus(1);
        record.setOperatorId(UserContext.get().id());
        record.setReason(reason == null || reason.isBlank() ? "实训室内调剂" : reason);
        adjustRecordMapper.insert(record);

        notifyService.send(studentId, "RESULT", "实训室内调剂成功",
                "你已被调剂至本实训室导师，请查看录取结果。", "ADJUST", record.getId());
    }

    private InterviewResult findOrCreate(Long applicationId, Long studentId, Long tutorId) {
        InterviewResult result = resultMapper.selectOne(new QueryWrapper<InterviewResult>()
                .eq("application_id", applicationId)
                .eq("tutor_id", tutorId));
        if (result == null) {
            result = new InterviewResult();
            result.setApplicationId(applicationId);
            result.setStudentId(studentId);
            result.setTutorId(tutorId);
            result.setRoundNo(phaseService.currentRound());
            result.setOfferStatus(0);
            result.setConfirmStatus(0);
            result.setVersion(0);
            resultMapper.insert(result);
        }
        return result;
    }

    /** 供阶段推进时清理过期提醒使用。 */
    public int touchTimeout() {
        return offerService.handleTimeouts();
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
