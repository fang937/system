package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.R;
import com.hbsd.labselect.service.PhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三端共用元数据：阶段状态、实训室与导师公开信息。 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final PhaseService phaseService;
    private final JdbcTemplate jdbc;

    @GetMapping("/phase")
    public R<Map<String, Object>> phase() {
        return R.ok(phaseService.now());
    }

    @GetMapping("/labs")
    public R<List<Map<String, Object>>> labs() {
        return R.ok(jdbc.queryForList("""
                SELECT l.id, l.code, l.name, l.description, l.location,
                       u.name AS leaderName,
                       l.tech_backend AS techBackend, l.tech_frontend AS techFrontend,
                       l.ai_direction AS aiDirection, l.output_types AS outputTypes,
                       l.achievements, l.training_mode AS trainingMode,
                       l.graduation_standard AS graduationStandard, l.suitable_for AS suitableFor,
                       (SELECT COUNT(*) FROM tutors t WHERE t.lab_id = l.id AND t.status = 1) AS tutorCount,
                       (SELECT COALESCE(SUM(t.quota), 0) FROM tutors t WHERE t.lab_id = l.id AND t.status = 1) AS totalQuota
                  FROM labs l LEFT JOIN users u ON u.id = l.leader_id
                 WHERE l.status = 1 ORDER BY l.code
                """));
    }

    /** 宣讲/开放日用的导师总览（不依赖学生身份，任何登录账号可看）。 */
    @GetMapping("/tutors")
    public R<List<Map<String, Object>>> tutors() {
        int round = phaseService.currentRound();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("round", round);
        return R.ok(jdbc.queryForList("""
                SELECT t.id AS tutorId, u.name AS tutorName, t.title, t.research_direction AS researchDirection,
                       t.intro, t.quota, t.remaining_quota AS remainingQuota,
                       l.id AS labId, l.name AS labName, l.description AS labDescription,
                       (SELECT COUNT(*) FROM applications a
                         WHERE a.round_no = ? AND (a.tutor_1_id = t.id OR a.tutor_2_id = t.id OR a.tutor_3_id = t.id))
                                                AS applicantCount
                  FROM tutors t JOIN users u ON u.id = t.user_id JOIN labs l ON l.id = t.lab_id
                 WHERE t.status = 1 AND u.status = 1
                 ORDER BY l.id, t.id
                """, round));
    }
}
