package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.R;
import com.hbsd.labselect.common.RequireRole;
import com.hbsd.labselect.dto.ManualAdjustReq;
import com.hbsd.labselect.dto.PhaseWindowReq;
import com.hbsd.labselect.dto.StudentSaveReq;
import com.hbsd.labselect.dto.TutorSaveReq;
import com.hbsd.labselect.ocr.OcrProviderResolver;
import com.hbsd.labselect.service.AdminService;
import com.hbsd.labselect.service.AdjustService;
import com.hbsd.labselect.service.PhaseService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole("SYS_ADMIN")
public class AdminController {

    private final AdminService adminService;
    private final AdjustService adjustService;
    private final OcrProviderResolver ocrProviderResolver;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(adminService.overview());
    }

    /* ---------- 简历解析引擎 ---------- */

    /** 当前引擎与各引擎可用性（密钥仅返回掩码） */
    @GetMapping("/ocr-config")
    public R<Map<String, Object>> ocrConfig() {
        return R.ok(ocrProviderResolver.snapshot());
    }

    /** 切换引擎：auto / mineru / pp-structure-v3 / ocr / none，保存后立即生效 */
    @PutMapping("/ocr-config")
    public R<Void> updateOcrConfig(@RequestBody Map<String, String> body) {
        ocrProviderResolver.update(body.get("provider"));
        return R.ok();
    }

    /** 写入 OCR 密钥/接入点：mineru-token / structure-endpoint / structure-api-key，立即生效并持久化 */
    @PutMapping("/ocr-secrets")
    public R<Void> updateOcrSecret(@RequestBody Map<String, String> body) {
        ocrProviderResolver.updateSecret(body.get("target"), body.get("value"));
        return R.ok();
    }

    /* ---------- 阶段与轮次 ---------- */

    @GetMapping("/phases")
    public R<List<Map<String, Object>>> phases() {
        return R.ok(adminService.phases());
    }

    /** 重置整体流程：清空志愿/面试/Offer/排期/调剂，恢复名额并回到「宣讲」阶段 */
    @PostMapping("/phases/reset")
    public R<Map<String, Object>> resetPhases() {
        return R.ok(adminService.resetAll());
    }

    @PutMapping("/phases/{id}")
    public R<Void> updatePhase(@PathVariable Long id, @RequestBody PhaseWindowReq req) {
        adminService.updatePhase(id, req);
        return R.ok();
    }

    @PostMapping("/phases/{id}/jump")
    public R<Void> jumpPhase(@PathVariable Long id) {
        adminService.jumpPhase(id);
        return R.ok();
    }

    @PostMapping("/phases/{id}/close")
    public R<Void> closePhase(@PathVariable Long id) {
        adminService.closePhase(id);
        return R.ok();
    }

    @PostMapping("/rounds/{round}")
    public R<Void> setRound(@PathVariable Integer round) {
        adminService.setRound(round);
        return R.ok();
    }

    /* ---------- 导师与实训室 ---------- */

    @GetMapping("/tutors")
    public R<List<Map<String, Object>>> tutors() {
        return R.ok(adminService.tutors());
    }

    @PostMapping("/tutors")
    public R<Void> createTutor(@RequestBody TutorSaveReq req) {
        adminService.saveTutor(req);
        return R.ok();
    }

    @PutMapping("/tutors/{id}")
    public R<Void> updateTutor(@PathVariable Long id, @RequestBody TutorSaveReq req) {
        adminService.saveTutor(new TutorSaveReq(id, req.username(), req.name(), req.labId(),
                req.title(), req.researchDirection(), req.intro(), req.quota(), req.priorityScore(),
                req.labLeader()));
        return R.ok();
    }

    @PostMapping("/tutors/{id}/toggle")
    public R<Void> toggleTutor(@PathVariable Long id, @RequestParam boolean enable) {
        adminService.toggleTutor(id, enable);
        return R.ok();
    }

    /**
     * 调整实训室成员职位（实训室负责人 / 实训讲师）。
     * body: {"position": "LEADER" | "TUTOR"}
     * 设为负责人时自动撤销该室原负责人，保证一室一负责人。
     */
    @PostMapping("/tutors/{id}/position")
    public R<Map<String, Object>> changeTutorPosition(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        return R.ok(adminService.changeTutorPosition(id, String.valueOf(body.get("position"))));
    }

    @GetMapping("/labs")
    public R<List<Map<String, Object>>> labs() {
        return R.ok(adminService.labs());
    }

    @PostMapping("/labs")
    public R<Void> createLab(@RequestBody Map<String, Object> body) {
        adminService.saveLab(null, body);
        return R.ok();
    }

    @PutMapping("/labs/{id}")
    public R<Void> updateLab(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminService.saveLab(id, body);
        return R.ok();
    }

    @GetMapping("/students")
    public R<List<Map<String, Object>>> students() {
        return R.ok(adminService.students());
    }

    /** 录入学生：创建登录账号（账号=学号，初始密码 123456）与学生档案。 */
    @PostMapping("/students")
    public R<Void> createStudent(@RequestBody StudentSaveReq req) {
        adminService.saveStudent(new StudentSaveReq(null, req.studentNo(), req.name(), req.major(),
                req.className(), req.grade(), req.gpa(), req.skillTags(), req.priorityScore(),
                req.phone(), req.email()));
        return R.ok();
    }

    /** 修改学生信息；学号可调整，但必须保持全库唯一。 */
    @PutMapping("/students/{id}")
    public R<Void> updateStudent(@PathVariable Long id, @RequestBody StudentSaveReq req) {
        adminService.saveStudent(new StudentSaveReq(id, req.studentNo(), req.name(), req.major(),
                req.className(), req.grade(), req.gpa(), req.skillTags(), req.priorityScore(),
                req.phone(), req.email()));
        return R.ok();
    }

    /**
     * 批量录入学生：请求体为数组，每个元素是一条学生记录（字段同单条录入）。
     * 返回 total / successCount / failCount / items（逐条成功失败与原因）。
     */
    @PostMapping("/students/batch")
    public R<Map<String, Object>> batchStudents(@RequestBody List<Map<String, Object>> rows) {
        return R.ok(adminService.saveStudents(rows));
    }

    /* ---------- 统筹调剂 ---------- */

    @GetMapping("/adjust/preview")
    public R<Map<String, Object>> adjustPreview() {
        return R.ok(adminService.adjustPreview());
    }

    @PostMapping("/adjust/run")
    public R<Map<String, Object>> adjustRun(@RequestParam(defaultValue = "2") int scope) {
        return R.ok(adjustService.run(scope, com.hbsd.labselect.common.UserContext.get().id()));
    }

    @PostMapping("/adjust/manual")
    public R<Void> adjustManual(@RequestBody ManualAdjustReq req) {
        adjustService.manual(req.studentId(), req.tutorId(),
                com.hbsd.labselect.common.UserContext.get().id(), req.reason());
        return R.ok();
    }

    /* ---------- 公示与导出 ---------- */

    @GetMapping("/results")
    public R<List<Map<String, Object>>> results() {
        return R.ok(adminService.results());
    }

    @GetMapping("/results/export")
    public void export(HttpServletResponse response) throws IOException {
        byte[] bytes = adminService.exportResults();
        String fileName = adminService.exportFileName();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private Long num(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }
}
