package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.DownloadResponse;
import com.hbsd.labselect.common.R;
import com.hbsd.labselect.common.RequireRole;
import com.hbsd.labselect.dto.AiScoreReq;
import com.hbsd.labselect.dto.OfferReq;
import com.hbsd.labselect.dto.ScheduleReq;
import com.hbsd.labselect.dto.ScoreReq;
import com.hbsd.labselect.service.ResumeService;
import com.hbsd.labselect.service.WorkbenchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 导师 + 实训室管理员合并工作台。
 * 类级只要求 TUTOR / LAB_ADMIN；具体能力由 Service 依据账号能力与实训室归属二次校验。
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@RequireRole({"TUTOR", "LAB_ADMIN"})
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final ResumeService resumeService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(workbenchService.overview());
    }

    @GetMapping("/applicants")
    public R<List<Map<String, Object>>> applicants(@RequestParam(required = false) Integer round) {
        return R.ok(workbenchService.applicants(round));
    }

    /** 导师查看报考自己的学生简历（鉴权：必须是该学生的志愿导师） */
    @GetMapping("/applicants/{applicationId}/resume")
    public ResponseEntity<?> applicantResume(@PathVariable Long applicationId) {
        try {
            var file = resumeService.downloadForTutor(applicationId);
            return DownloadResponse.of(file.resource(), file.fileName(), file.contentType());
        } catch (BizException e) {
            return DownloadResponse.error(e.getCode(), e.getMessage());
        }
    }

    @PostMapping("/applicants/{applicationId}/score")
    public R<Void> score(@PathVariable Long applicationId, @Valid @RequestBody ScoreReq req) {
        workbenchService.saveScore(applicationId, req.score(), req.scoreDetail(), req.remark());
        return R.ok();
    }

    /** 多维评分：按评分标准折算总分，同时保留维度明细 */
    @PostMapping("/applicants/{applicationId}/scores")
    public R<Void> scores(@PathVariable Long applicationId, @RequestBody AiScoreReq req) {
        workbenchService.saveScores(applicationId, req.items());
        return R.ok();
    }

    @PostMapping("/applicants/{applicationId}/decision")
    public R<Void> decide(@PathVariable Long applicationId, @RequestBody OfferReq req) {
        int status = req.offerStatus() == null ? 0 : req.offerStatus();
        workbenchService.decide(applicationId, status, req.remark());
        return R.ok();
    }

    @GetMapping("/offers")
    public R<List<Map<String, Object>>> offers(@RequestParam(required = false) Integer round) {
        return R.ok(workbenchService.myOffers(round));
    }

    @GetMapping("/schedules")
    public R<List<Map<String, Object>>> schedules(@RequestParam(required = false) Integer round) {
        return R.ok(workbenchService.schedules(round));
    }

    @PostMapping("/schedules")
    public R<Void> createSchedule(@RequestBody ScheduleReq req) {
        workbenchService.saveSchedule(null, req);
        return R.ok();
    }

    @PutMapping("/schedules/{id}")
    public R<Void> updateSchedule(@PathVariable Long id, @RequestBody ScheduleReq req) {
        workbenchService.saveSchedule(id, req);
        return R.ok();
    }

    @DeleteMapping("/schedules/{id}")
    public R<Void> deleteSchedule(@PathVariable Long id) {
        workbenchService.deleteSchedule(id);
        return R.ok();
    }

    @GetMapping("/lab/progress")
    public R<Map<String, Object>> labProgress(@RequestParam(required = false) Integer round) {
        return R.ok(workbenchService.labProgress(round));
    }

    @GetMapping("/lab/students")
    public R<List<Map<String, Object>>> labStudents(@RequestParam(required = false) Integer round) {
        return R.ok(workbenchService.labStudents(round));
    }

    @GetMapping("/lab/pool")
    public R<List<Map<String, Object>>> labPool() {
        return R.ok(workbenchService.labPool());
    }

    @GetMapping("/lab/vacancies")
    public R<List<Map<String, Object>>> labVacancies() {
        return R.ok(workbenchService.labVacancies());
    }

    @PostMapping("/lab/adjust")
    public R<Void> labAdjust(@RequestBody Map<String, Object> body) {
        Long studentId = body.get("studentId") == null ? null : Long.valueOf(body.get("studentId").toString());
        Long tutorId = body.get("tutorId") == null ? null : Long.valueOf(body.get("tutorId").toString());
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        if (studentId == null || tutorId == null) {
            return R.fail(400, "请选择学生与导师");
        }
        workbenchService.labAdjust(studentId, tutorId, reason);
        return R.ok();
    }
}
