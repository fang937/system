package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.dto.PhaseWindowReq;
import com.hbsd.labselect.dto.TutorSaveReq;
import com.hbsd.labselect.entity.Lab;
import com.hbsd.labselect.entity.SelectionPhase;
import com.hbsd.labselect.entity.Tutor;
import com.hbsd.labselect.entity.User;
import com.hbsd.labselect.mapper.LabMapper;
import com.hbsd.labselect.mapper.SelectionPhaseMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import com.hbsd.labselect.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学院/系统管理员端：阶段时间轴、轮次流转、导师与实训室维护、全局看板、结果导出。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;
    private final TutorMapper tutorMapper;
    private final LabMapper labMapper;
    private final SelectionPhaseMapper phaseMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhaseService phaseService;
    private final AdjustService adjustService;

    public Map<String, Object> overview() {
        int round = phaseService.currentRound();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phase", phaseService.now());
        map.put("round", round);
        map.put("stats", jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND status = 1)         AS studentCount,
                       (SELECT COUNT(*) FROM applications WHERE round_no = ?)                     AS submittedCount,
                       (SELECT COUNT(*) FROM applications WHERE round_no = ? AND final_tutor_id IS NOT NULL) AS matchedCount,
                       (SELECT COALESCE(SUM(quota), 0) FROM tutors WHERE status = 1)               AS totalQuota,
                       (SELECT COALESCE(SUM(remaining_quota), 0) FROM tutors WHERE status = 1)     AS remainingQuota,
                       (SELECT COUNT(*) FROM interview_results WHERE round_no = ? AND offer_status = 1) AS offerCount,
                       (SELECT COUNT(*) FROM interview_results WHERE round_no = ? AND offer_status = 1
                                                                 AND confirm_status = 1)           AS confirmedCount,
                       (SELECT COUNT(*) FROM interview_results WHERE round_no = ? AND offer_status = 2) AS reserveCount,
                       (SELECT COUNT(*) FROM interview_results WHERE round_no = ? AND offer_status = 3) AS rejectedCount,
                       (SELECT COUNT(*) FROM tutors WHERE status = 1)                              AS tutorCount,
                       (SELECT COUNT(*) FROM labs WHERE status = 1)                                AS labCount
                """, round, round, round, round, round, round));
        map.put("labs", labProgress());
        map.put("recentMatches", jdbc.queryForList("""
                SELECT u.name AS studentName, tu.name AS tutorName, l.name AS labName,
                       a.round_no AS roundNo, a.updated_at AS matchedAt
                  FROM applications a
                  JOIN users u   ON u.id = a.student_id
                  JOIN tutors t  ON t.id = a.final_tutor_id
                  JOIN users tu  ON tu.id = t.user_id
                  JOIN labs l    ON l.id = t.lab_id
                 WHERE a.final_tutor_id IS NOT NULL
                 ORDER BY a.updated_at DESC LIMIT 8
                """));
        return map;
    }

    public List<Map<String, Object>> labProgress() {
        int round = phaseService.currentRound();
        return jdbc.queryForList("""
                SELECT l.id AS labId, l.name AS labName, l.location,
                       COUNT(t.id)                            AS tutorCount,
                       COALESCE(SUM(t.quota), 0)              AS totalQuota,
                       COALESCE(SUM(t.remaining_quota), 0)    AS remainingQuota,
                       COALESCE(SUM(t.quota - t.remaining_quota), 0) AS lockedQuota,
                       (SELECT COUNT(DISTINCT a.student_id) FROM applications a
                          JOIN tutors t2 ON t2.lab_id = l.id
                         WHERE a.round_no = ?
                           AND (a.tutor_1_id = t2.id OR a.tutor_2_id = t2.id OR a.tutor_3_id = t2.id)) AS applicantCount
                  FROM labs l
                  LEFT JOIN tutors t ON t.lab_id = l.id AND t.status = 1
                 WHERE l.status = 1
                 GROUP BY l.id, l.name, l.location
                 ORDER BY l.id
                """, round);
    }

    /* ---------------- 阶段与轮次 ---------------- */

    public List<Map<String, Object>> phases() {
        return phaseService.list().stream().map(phaseService::phaseMap).toList();
    }

    @Transactional
    public void updatePhase(Long id, PhaseWindowReq req) {
        SelectionPhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("阶段不存在");
        }
        if (req.startAt() == null || req.endAt() == null) {
            throw new BizException("请填写完整的起止时间");
        }
        if (!req.endAt().isAfter(req.startAt())) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        phase.setStartAt(req.startAt());
        phase.setEndAt(req.endAt());
        phaseMapper.updateById(phase);
        refreshStatuses();
    }

    /** 立即进入：把窗口整体平移到当前时间，保留原时长（演示与紧急调整用）。 */
    @Transactional
    public void jumpPhase(Long id) {
        SelectionPhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("阶段不存在");
        }
        Duration duration = Duration.between(phase.getStartAt(), phase.getEndAt());
        if (duration.isNegative() || duration.isZero()) {
            duration = Duration.ofDays(1);
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        phase.setStartAt(now);
        phase.setEndAt(now.plus(duration));
        phaseMapper.updateById(phase);
        if (phase.getRoundNo() != null) {
            phaseService.setCurrentRound(phase.getRoundNo());
        }
        refreshStatuses();
    }

    /** 立即结束：把结束时间拉到当前，用于提前关闭阶段。 */
    @Transactional
    public void closePhase(Long id) {
        SelectionPhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("阶段不存在");
        }
        phase.setEndAt(LocalDateTime.now().withNano(0));
        phaseMapper.updateById(phase);
        refreshStatuses();
    }

    public void setRound(Integer round) {
        if (round == null || round < 1 || round > 3) {
            throw new BizException("轮次只能是 1~3");
        }
        phaseService.setCurrentRound(round);
    }

    private void refreshStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (SelectionPhase phase : phaseService.list()) {
            String status = now.isBefore(phase.getStartAt()) ? "PENDING"
                    : now.isAfter(phase.getEndAt()) ? "CLOSED" : "ACTIVE";
            if (!status.equals(phase.getStatus())) {
                phase.setStatus(status);
                phaseMapper.updateById(phase);
            }
        }
    }

    /* ---------------- 导师与实训室 ---------------- */

    public List<Map<String, Object>> tutors() {
        return jdbc.queryForList("""
                SELECT t.id AS tutorId, t.user_id AS userId, u.username, u.name AS tutorName,
                       u.role AS userRole, u.status AS accountStatus,
                       CASE u.role WHEN 'LAB_ADMIN' THEN 'LEADER' ELSE 'TUTOR' END AS position,
                       CASE u.role WHEN 'LAB_ADMIN' THEN '实训室负责人' ELSE '实训讲师' END AS positionLabel,
                       CASE WHEN l.leader_id = u.id THEN 1 ELSE 0 END AS labLeader,
                       t.lab_id AS labId, l.name AS labName,
                       t.title, t.research_direction AS researchDirection, t.intro,
                       t.quota, t.remaining_quota AS remainingQuota, t.status,
                       (SELECT COUNT(*) FROM applications a
                         WHERE a.round_no = ? AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id))
                                                    AS applicantCount
                  FROM tutors t
                  JOIN users u ON u.id = t.user_id
                  JOIN labs l  ON l.id = t.lab_id
                 ORDER BY t.lab_id, t.id
                """, phaseService.currentRound());
    }

    @Transactional
    public void saveTutor(TutorSaveReq req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException("请填写导师姓名");
        }
        if (req.labId() == null) {
            throw new BizException("请选择所属实训室");
        }
        int quota = req.quota() == null ? 0 : req.quota();
        if (quota < 0) {
            throw new BizException("名额不能为负数");
        }
        Lab lab = labMapper.selectById(req.labId());
        if (lab == null) {
            throw new BizException("实训室不存在");
        }

        if (req.id() == null) {
            if (req.username() == null || req.username().isBlank()) {
                throw new BizException("请填写登录账号");
            }
            Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("username", req.username()));
            if (exists != null && exists > 0) {
                throw new BizException("登录账号已存在");
            }
            User user = new User();
            user.setUsername(req.username().trim());
            user.setName(req.name());
            user.setRole(Boolean.TRUE.equals(req.labLeader()) ? "LAB_ADMIN" : "TUTOR");
            user.setLabId(req.labId());
            user.setStatus(1);
            user.setPassword(passwordEncoder.encode("123456"));
            userMapper.insert(user);

            Tutor tutor = new Tutor();
            tutor.setUserId(user.getId());
            tutor.setLabId(req.labId());
            tutor.setTitle(req.title());
            tutor.setResearchDirection(req.researchDirection());
            tutor.setIntro(req.intro());
            tutor.setQuota(quota);
            tutor.setRemainingQuota(quota);
            tutor.setVersion(0);
            tutor.setStatus(1);
            tutorMapper.insert(tutor);

            if (Boolean.TRUE.equals(req.labLeader())) {
                assignLabLeader(req.labId(), user.getId());
            }
            return;
        }

        Tutor tutor = tutorMapper.selectById(req.id());
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        int used = tutor.getQuota() - tutor.getRemainingQuota();
        if (quota < used) {
            throw new BizException("已锁定 " + used + " 个名额，总名额不能小于该数量");
        }
        tutor.setQuota(quota);
        tutor.setRemainingQuota(quota - used);
        tutor.setLabId(req.labId());
        tutor.setTitle(req.title());
        tutor.setResearchDirection(req.researchDirection());
        tutor.setIntro(req.intro());
        tutorMapper.updateById(tutor);

        User user = userMapper.selectById(tutor.getUserId());
        if (user != null) {
            user.setName(req.name());
            user.setLabId(req.labId());
            userMapper.updateById(user);
            if (req.labLeader() != null) {
                applyLabLeader(tutor.getUserId(), req.labId(), Boolean.TRUE.equals(req.labLeader()));
            }
        }
    }

    /**
     * 授予 / 撤销实训室负责人身份：
     * 负责人 = users.role 变为 LAB_ADMIN（导师工作台内多出「实训室事务」）+ 该实训室 leader_id 指向本人。
     * 一个实训室只保留一位负责人，改派前需先取消原负责人，避免静默剥夺他人权限。
     */
    private void applyLabLeader(Long userId, Long labId, boolean leader) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if (leader) {
            assignLabLeader(labId, userId);
            if (!"LAB_ADMIN".equals(user.getRole())) {
                user.setRole("LAB_ADMIN");
                userMapper.updateById(user);
            }
            return;
        }
        jdbc.update("UPDATE labs SET leader_id = NULL WHERE leader_id = ?", userId);
        if ("LAB_ADMIN".equals(user.getRole())) {
            user.setRole("TUTOR");
            userMapper.updateById(user);
        }
    }

    private void assignLabLeader(Long labId, Long userId) {
        Lab lab = labMapper.selectById(labId);
        if (lab == null) {
            throw new BizException("实训室不存在");
        }
        // 自动撤销该室原负责人（若非本人），保证一室一负责人，无需管理员先手动取消
        if (lab.getLeaderId() != null && !lab.getLeaderId().equals(userId)) {
            User previous = userMapper.selectById(lab.getLeaderId());
            if (previous != null && "LAB_ADMIN".equals(previous.getRole())) {
                previous.setRole("TUTOR");
                userMapper.updateById(previous);
            }
        }
        // 一位导师最多负责一个实训室：清除其他室指向本人的 leader_id
        jdbc.update("UPDATE labs SET leader_id = NULL WHERE leader_id = ? AND id <> ?", userId, labId);
        jdbc.update("UPDATE labs SET leader_id = ? WHERE id = ?", userId, labId);
    }

    /* ---------------- 职位调整：实训室负责人 / 实训讲师 ----------------
       职位编码：users.role = LAB_ADMIN → 实训室负责人（LEADER）
                          TUTOR    → 实训讲师（TUTOR）
       labs.leader_id 记录每室负责人的账号 id，是「一室一负责人」的权威约束。
       下方方法是唯一的职位变更入口，saveTutor 的负责人开关也走同一套规则。
    */

    @Transactional
    public Map<String, Object> changeTutorPosition(Long tutorId, String position) {
        if (position == null || position.isBlank()) {
            throw new BizException("请指定目标职位");
        }
        boolean toLeader = "LEADER".equalsIgnoreCase(position);
        if (!toLeader && !"TUTOR".equalsIgnoreCase(position)) {
            throw new BizException("职位仅支持 LEADER（实训室负责人）或 TUTOR（实训讲师）");
        }
        Tutor tutor = tutorMapper.selectById(tutorId);
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        User user = userMapper.selectById(tutor.getUserId());
        if (user == null) {
            throw new BizException("导师账号不存在");
        }
        String role = user.getRole();
        if (!"TUTOR".equals(role) && !"LAB_ADMIN".equals(role)) {
            throw new BizException("该账号不是实训室成员，无法调整职位");
        }
        Long labId = tutor.getLabId();
        Lab lab = labMapper.selectById(labId);
        if (lab == null) {
            throw new BizException("所属实训室不存在");
        }
        boolean wasLeader = "LAB_ADMIN".equals(role);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tutorId", tutorId);
        result.put("userId", user.getId());
        result.put("tutorName", user.getName());
        result.put("labId", labId);
        result.put("labName", lab.getName());

        if (toLeader) {
            // 升为负责人：自动撤销该室原负责人，本人 role 升为 LAB_ADMIN
            if (lab.getLeaderId() != null && !lab.getLeaderId().equals(user.getId())) {
                User previous = userMapper.selectById(lab.getLeaderId());
                if (previous != null && "LAB_ADMIN".equals(previous.getRole())) {
                    previous.setRole("TUTOR");
                    userMapper.updateById(previous);
                    result.put("demotedPreviousLeader", previous.getName());
                }
            }
            if (!"LAB_ADMIN".equals(user.getRole())) {
                user.setRole("LAB_ADMIN");
                userMapper.updateById(user);
            }
            jdbc.update("UPDATE labs SET leader_id = NULL WHERE leader_id = ? AND id <> ?", user.getId(), labId);
            jdbc.update("UPDATE labs SET leader_id = ? WHERE id = ?", user.getId(), labId);
            result.put("position", "LEADER");
            result.put("positionLabel", "实训室负责人");
        } else {
            // 降为讲师：本人 role 降为 TUTOR；若原是本室负责人，leader_id 清空（实训室进入无负责人状态）
            if (wasLeader && lab.getLeaderId() != null && lab.getLeaderId().equals(user.getId())) {
                jdbc.update("UPDATE labs SET leader_id = NULL WHERE id = ?", labId);
                result.put("labLeaderless", true);
            }
            if ("LAB_ADMIN".equals(user.getRole())) {
                user.setRole("TUTOR");
                userMapper.updateById(user);
            }
            result.put("position", "TUTOR");
            result.put("positionLabel", "实训讲师");
        }
        return result;
    }

    @Transactional
    public void toggleTutor(Long tutorId, boolean enable) {
        Tutor tutor = tutorMapper.selectById(tutorId);
        if (tutor == null) {
            throw new BizException("导师不存在");
        }
        tutor.setStatus(enable ? 1 : 0);
        tutorMapper.updateById(tutor);
        User user = userMapper.selectById(tutor.getUserId());
        if (user != null) {
            user.setStatus(enable ? 1 : 0);
            userMapper.updateById(user);
        }
    }

    public List<Map<String, Object>> labs() {
        return jdbc.queryForList("""
                SELECT l.id, l.code, l.name, l.description, l.location, l.leader_id AS leaderId,
                       u.name AS leaderName, l.status,
                       l.tech_backend AS techBackend, l.tech_frontend AS techFrontend,
                       l.ai_direction AS aiDirection, l.output_types AS outputTypes,
                       l.achievements, l.training_mode AS trainingMode,
                       l.graduation_standard AS graduationStandard, l.suitable_for AS suitableFor,
                       (SELECT COUNT(*) FROM tutors t WHERE t.lab_id = l.id AND t.status = 1) AS tutorCount,
                       (SELECT COALESCE(SUM(t.quota), 0) FROM tutors t WHERE t.lab_id = l.id AND t.status = 1) AS totalQuota
                  FROM labs l LEFT JOIN users u ON u.id = l.leader_id
                 ORDER BY l.code
                """);
    }

    @Transactional
    public void saveLab(Long id, Map<String, Object> body) {
        String name = text(body.get("name"));
        if (name == null || name.isBlank()) {
            throw new BizException("请填写实训室名称");
        }
        Lab lab;
        if (id == null) {
            lab = new Lab();
            lab.setStatus(1);
            String code = text(body.get("code"));
            lab.setCode(code == null || code.isBlank() ? "LAB" + System.currentTimeMillis() % 10000 : code);
        } else {
            lab = labMapper.selectById(id);
            if (lab == null) {
                throw new BizException("实训室不存在");
            }
            lab.setCode(text(body.get("code")));
        }
        lab.setName(name);
        lab.setDescription(blankToNull(text(body.get("description"))));
        lab.setLocation(blankToNull(text(body.get("location"))));
        lab.setLeaderId(body.get("leaderId") == null ? null : Long.valueOf(String.valueOf(body.get("leaderId"))));
        lab.setTechBackend(blankToNull(text(body.get("techBackend"))));
        lab.setTechFrontend(blankToNull(text(body.get("techFrontend"))));
        lab.setAiDirection(blankToNull(text(body.get("aiDirection"))));
        lab.setOutputTypes(blankToNull(text(body.get("outputTypes"))));
        lab.setAchievements(blankToNull(text(body.get("achievements"))));
        lab.setTrainingMode(blankToNull(text(body.get("trainingMode"))));
        lab.setGraduationStandard(blankToNull(text(body.get("graduationStandard"))));
        lab.setSuitableFor(blankToNull(text(body.get("suitableFor"))));
        if (id == null) {
            labMapper.insert(lab);
        } else {
            labMapper.updateById(lab);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    public List<Map<String, Object>> students() {
        int round = phaseService.currentRound();
        return jdbc.queryForList("""
                SELECT u.id AS studentId, u.username, u.name AS studentName, u.student_no AS studentNo,
                       sp.major, sp.class_name AS className, sp.grade, sp.gpa,
                       sp.skill_tags AS skillTags, sp.priority_score AS priorityScore,
                       a.id AS applicationId, a.status AS applicationStatus, a.final_tutor_id AS finalTutorId,
                       tu.name AS finalTutorName, l.name AS finalLabName
                  FROM users u
                  LEFT JOIN student_profiles sp ON sp.user_id = u.id
                  LEFT JOIN applications a ON a.student_id = u.id AND a.round_no = ?
                  LEFT JOIN tutors t ON t.id = a.final_tutor_id
                  LEFT JOIN users tu ON tu.id = t.user_id
                  LEFT JOIN labs l ON l.id = t.lab_id
                 WHERE u.role = 'STUDENT'
                 ORDER BY u.student_no
                """, round);
    }

    /* ---------------- 结果公示与导出 ---------------- */

    public List<Map<String, Object>> results() {
        return jdbc.queryForList("""
                SELECT u.name AS studentName, u.student_no AS studentNo, sp.major, sp.class_name AS className,
                       l.name AS labName, tu.name AS tutorName, t.title,
                       a.round_no AS roundNo, r.score,
                       (SELECT ar.adjust_type FROM adjust_records ar WHERE ar.student_id = u.id ORDER BY ar.id DESC LIMIT 1) AS adjustType
                  FROM applications a
                  JOIN users u  ON u.id = a.student_id
                  JOIN tutors t ON t.id = a.final_tutor_id
                  JOIN users tu ON tu.id = t.user_id
                  JOIN labs l   ON l.id = t.lab_id
                  LEFT JOIN student_profiles sp ON sp.user_id = u.id
                  LEFT JOIN interview_results r ON r.student_id = u.id AND r.tutor_id = a.final_tutor_id
                 WHERE a.final_tutor_id IS NOT NULL
                 ORDER BY l.id, tu.name, u.student_no
                """);
    }

    public Map<String, Object> adjustPreview() {
        return adjustService.preview();
    }

    public byte[] exportResults() throws IOException {
        List<Map<String, Object>> rows = results();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("录取名单");
            String[] headers = {"序号", "学号", "姓名", "专业", "班级", "实训室", "导师", "职称", "轮次", "面试成绩", "录取方式"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int index = 1;
            for (Map<String, Object> row : rows) {
                Row r = sheet.createRow(index);
                r.createCell(0).setCellValue(index);
                r.createCell(1).setCellValue(text(row.get("studentNo")));
                r.createCell(2).setCellValue(text(row.get("studentName")));
                r.createCell(3).setCellValue(text(row.get("major")));
                r.createCell(4).setCellValue(text(row.get("className")));
                r.createCell(5).setCellValue(text(row.get("labName")));
                r.createCell(6).setCellValue(text(row.get("tutorName")));
                r.createCell(7).setCellValue(text(row.get("title")));
                r.createCell(8).setCellValue("第 " + text(row.get("roundNo")) + " 轮");
                r.createCell(9).setCellValue(text(row.get("score")));
                r.createCell(10).setCellValue(adjustLabel(row.get("adjustType")));
                index++;
            }
            int[] widths = {6, 14, 10, 16, 14, 20, 10, 10, 10, 10, 16};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public String exportFileName() {
        return "录取名单-" + LocalDateTime.now().format(TS) + ".xlsx";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String adjustLabel(Object type) {
        if (type == null) {
            return "双选录取";
        }
        int t = ((Number) type).intValue();
        return switch (t) {
            case 1 -> "实训室内调剂";
            case 2 -> "全院调剂";
            case 3 -> "人工兜底";
            default -> "双选录取";
        };
    }
}
