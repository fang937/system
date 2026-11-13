package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.dto.ApplyReq;
import com.hbsd.labselect.entity.Application;
import com.hbsd.labselect.entity.Tutor;
import com.hbsd.labselect.mapper.ApplicationMapper;
import com.hbsd.labselect.mapper.InterviewResultMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final JdbcTemplate jdbc;
    private final ApplicationMapper applicationMapper;
    private final TutorMapper tutorMapper;
    private final InterviewResultMapper resultMapper;
    private final PhaseService phaseService;
    private final OfferService offerService;

    /** 导师与项目方向（宣讲/开放日/填报阶段都可见）。 */
    public List<Map<String, Object>> tutors() {
        int round = phaseService.currentRound();
        String sql = """
                SELECT t.id                        AS tutorId,
                       t.user_id                   AS userId,
                       u.name                      AS tutorName,
                       t.title                     AS title,
                       t.research_direction        AS researchDirection,
                       t.intro                     AS intro,
                       t.quota                     AS quota,
                       t.remaining_quota           AS remainingQuota,
                       l.id                        AS labId,
                       l.name                      AS labName,
                       l.description               AS labDescription,
                       (SELECT COUNT(*) FROM applications a
                         WHERE a.round_no = ? AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id))
                                                   AS applicantCount
                  FROM tutors t
                  JOIN users u ON u.id = t.user_id
                  JOIN labs l  ON l.id = t.lab_id
                 WHERE t.status = 1 AND u.status = 1
                 ORDER BY l.id, t.id
                """;
        return jdbc.queryForList(sql, round);
    }

    /** 我的志愿（当前轮次）。 */
    public Map<String, Object> myApplication() {
        Long studentId = UserContext.get().id();
        int round = phaseService.currentRound();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, round_no AS roundNo, tutor_1_id AS tutor1Id, tutor_2_id AS tutor2Id, tutor_3_id AS tutor3Id,
                       accept_lab_adjust AS acceptLabAdjust, accept_global_adjust AS acceptGlobalAdjust,
                       status, submitted_at AS submittedAt, final_tutor_id AS finalTutorId
                  FROM applications WHERE student_id = ? AND round_no = ?
                """, studentId, round);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("round", round);
        result.put("applyWindow", phaseService.phaseMap(phaseService.find(PhaseService.APPLY, round).orElse(null)));
        if (rows.isEmpty()) {
            result.put("application", null);
            result.put("choices", List.of());
            return result;
        }
        Map<String, Object> application = new LinkedHashMap<>(rows.get(0));
        List<Long> ids = new ArrayList<>();
        for (String key : List.of("tutor1Id", "tutor2Id", "tutor3Id")) {
            Object v = application.get(key);
            if (v instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        List<Map<String, Object>> choices = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", i + 1);
            choice.put("tutorId", ids.get(i));
            List<Map<String, Object>> tutorRows = jdbc.queryForList("""
                    SELECT u.name AS tutorName, t.title, t.research_direction AS researchDirection,
                           l.name AS labName, t.quota, t.remaining_quota AS remainingQuota,
                           (SELECT COUNT(*) FROM applications a
                             WHERE a.round_no = ?
                               AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id))
                                                     AS applicantCount
                      FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                     WHERE t.id = ?
                    """, round, ids.get(i));
            choice.putAll(tutorRows.isEmpty() ? Map.of("tutorName", "（导师已删除）") : tutorRows.get(0));
            choices.add(choice);
        }
        result.put("application", application);
        result.put("choices", choices);
        return result;
    }

    /** 阶段 3：提交/修改平行志愿。 */
    @Transactional
    public void submit(ApplyReq req) {
        Long studentId = UserContext.get().id();
        phaseService.require(PhaseService.APPLY);
        int round = phaseService.currentRound();

        List<Long> tutorIds = req.tutorIds() == null ? List.of() : req.tutorIds();
        LinkedHashSet<Long> unique = new LinkedHashSet<>(tutorIds);
        if (unique.isEmpty()) {
            throw new BizException("至少填报 1 个志愿");
        }
        if (unique.size() > 3) {
            throw new BizException("最多填报 3 个平行志愿");
        }
        for (Long tutorId : unique) {
            if (tutorId == null) {
                throw new BizException("志愿信息不完整");
            }
            Tutor tutor = tutorMapper.selectById(tutorId);
            if (tutor == null || tutor.getStatus() == null || tutor.getStatus() == 0) {
                throw new BizException("所选导师不存在或已停止招生");
            }
        }

        List<Application> matched = applicationMapper.selectList(new QueryWrapper<Application>()
                .eq("student_id", studentId)
                .isNotNull("final_tutor_id"));
        if (!matched.isEmpty()) {
            throw new BizException("你已被录取，不能再修改志愿");
        }

        List<Long> ids = new ArrayList<>(unique);
        Application application = applicationMapper.selectOne(new QueryWrapper<Application>()
                .eq("student_id", studentId)
                .eq("round_no", round));
        boolean create = application == null;
        if (create) {
            application = new Application();
            application.setStudentId(studentId);
            application.setRoundNo(round);
        } else if ("MATCHED".equals(application.getStatus())) {
            throw new BizException("本轮志愿已锁定，无法修改");
        }
        application.setTutor1Id(ids.get(0));
        application.setTutor2Id(ids.size() > 1 ? ids.get(1) : null);
        application.setTutor3Id(ids.size() > 2 ? ids.get(2) : null);
        application.setAcceptLabAdjust(Boolean.TRUE.equals(req.acceptLabAdjust()) ? 1 : 0);
        application.setAcceptGlobalAdjust(Boolean.TRUE.equals(req.acceptGlobalAdjust()) ? 1 : 0);
        application.setStatus("SUBMITTED");
        application.setSubmittedAt(LocalDateTime.now());
        if (create) {
            applicationMapper.insert(application);
        } else {
            applicationMapper.updateById(application);
        }
    }

    /** 我的 Offer 列表。 */
    public List<Map<String, Object>> myOffers() {
        Long studentId = UserContext.get().id();
        return jdbc.queryForList("""
                SELECT r.id, r.round_no AS roundNo, r.tutor_id AS tutorId, u.name AS tutorName, t.title,
                       t.research_direction AS researchDirection, l.name AS labName,
                       r.score, r.offer_status AS offerStatus, r.reserve_rank AS reserveRank,
                       r.confirm_status AS confirmStatus, r.offer_time AS offerTime,
                       r.confirm_deadline AS confirmDeadline, r.confirm_time AS confirmTime
                  FROM interview_results r
                  JOIN tutors t ON t.id = r.tutor_id
                  JOIN users u  ON u.id = t.user_id
                  JOIN labs l   ON l.id = t.lab_id
                 WHERE r.student_id = ? AND r.offer_status IN (1, 2, 3)
                 ORDER BY r.round_no DESC, r.offer_status ASC, r.id
                """, studentId);
    }

    public void confirm(Long resultId) {
        phaseService.require(PhaseService.CONFIRM);
        offerService.confirm(resultId, UserContext.get().id());
    }

    public void decline(Long resultId) {
        phaseService.require(PhaseService.CONFIRM);
        offerService.decline(resultId, UserContext.get().id());
    }

    /** 录取结果（阶段 8 公示后才展示最终名单）。 */
    public Map<String, Object> myResult() {
        Long studentId = UserContext.get().id();
        Map<String, Object> result = new LinkedHashMap<>();
        boolean published = phaseService.inWindow(PhaseService.PUBLISH);
        result.put("published", published);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.round_no AS roundNo, a.final_tutor_id AS finalTutorId, a.status,
                       a.updated_at AS matchedAt
                  FROM applications a
                 WHERE a.student_id = ? AND a.final_tutor_id IS NOT NULL
                 ORDER BY a.round_no DESC LIMIT 1
                """, studentId);
        if (rows.isEmpty()) {
            result.put("matched", false);
            result.put("message", published ? "未查询到录取记录，请关注调剂通知" : "结果尚未公示");
            return result;
        }
        Map<String, Object> row = rows.get(0);
        Long tutorId = ((Number) Objects.requireNonNull(row.get("finalTutorId"))).longValue();
        List<Map<String, Object>> tutorRows = jdbc.queryForList("""
                SELECT u.name AS tutorName, t.title, t.research_direction AS researchDirection,
                       l.name AS labName, l.location AS labLocation
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.id = ?
                """, tutorId);
        result.put("matched", true);
        result.put("tutorId", tutorId);
        result.put("detail", tutorRows.isEmpty() ? Map.of() : tutorRows.get(0));
        result.put("round", row.get("roundNo"));
        result.put("matchedAt", row.get("matchedAt"));
        return result;
    }

    /** 个人档案概览（学生端顶部卡片）。 */
    public Map<String, Object> profile() {
        Long studentId = UserContext.get().id();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.name, u.student_no AS studentNo, sp.major, sp.class_name AS className,
                       sp.grade, sp.gpa, sp.skill_tags AS skillTags, sp.priority_score AS priorityScore
                  FROM users u LEFT JOIN student_profiles sp ON sp.user_id = u.id
                 WHERE u.id = ?
                """, studentId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> notifications() {
        return jdbc.queryForList("""
                SELECT id, type, title, content, is_read AS isRead, created_at AS createdAt
                  FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50
                """, UserContext.get().id());
    }

    public void readNotification(Long id) {
        jdbc.update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = ? AND user_id = ?",
                id, UserContext.get().id());
    }

    public void readAll() {
        jdbc.update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = ? AND is_read = 0",
                UserContext.get().id());
    }

    /** 未读通知数，供顶栏红点使用。 */
    public int unreadCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
                Integer.class, UserContext.get().id());
        return count == null ? 0 : count;
    }

    /** 保留：供仪表盘统计本人 Offer 数。 */
    public long offerCount() {
        Long studentId = UserContext.get().id();
        return resultMapper.selectCount(new QueryWrapper<com.hbsd.labselect.entity.InterviewResult>()
                .eq("student_id", studentId)
                .eq("offer_status", 1));
    }
}
