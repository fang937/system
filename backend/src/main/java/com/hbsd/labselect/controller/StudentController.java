package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.DownloadResponse;
import com.hbsd.labselect.common.R;
import com.hbsd.labselect.common.RequireRole;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.dto.ApplyReq;
import com.hbsd.labselect.service.ResumeDraftService;
import com.hbsd.labselect.service.ResumeService;
import com.hbsd.labselect.service.StudentService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@RequireRole("STUDENT")
public class StudentController {

    private final StudentService studentService;
    private final ResumeService resumeService;
    private final ResumeDraftService resumeDraftService;

    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        return R.ok(studentService.profile());
    }

    /* ---------- 简历 ---------- */

    @GetMapping("/resume")
    public R<Map<String, Object>> resume() {
        return R.ok(resumeService.meta(UserContext.get().id()));
    }

    @PostMapping("/resume")
    public R<Map<String, Object>> uploadResume(@RequestParam("file") MultipartFile file) {
        return R.ok(resumeService.upload(UserContext.get().id(), file));
    }

    /* ---------- 结构化简历草稿（AI 优化简历的编辑底稿） ---------- */

    @GetMapping("/resume/draft")
    public R<Map<String, Object>> resumeDraft() {
        return R.ok(resumeDraftService.load(UserContext.get().id()));
    }

    @PutMapping("/resume/draft")
    public R<Map<String, Object>> saveResumeDraft(@RequestBody Map<String, Object> body) {
        return R.ok(resumeDraftService.save(UserContext.get().id(), body));
    }

    /** 从已上传的 PDF / DOCX 简历正文按栏目导入，导入结果仍需学生确认后保存 */
    @PostMapping("/resume/draft/import")
    public R<Map<String, Object>> importResumeDraft() {
        return R.ok(resumeDraftService.importFromResume(UserContext.get().id()));
    }

    @DeleteMapping("/resume")
    public R<Void> deleteResume() {
        resumeService.delete(UserContext.get().id());
        return R.ok();
    }

    /** 本人下载（带鉴权，文件不暴露为静态资源） */
    @GetMapping("/resume/file")
    public ResponseEntity<?> resumeFile() {
        try {
            var file = resumeService.downloadForStudent(UserContext.get().id());
            return DownloadResponse.of(file.resource(), file.fileName(), file.contentType());
        } catch (BizException e) {
            return DownloadResponse.error(e.getCode(), e.getMessage());
        }
    }

    @GetMapping("/tutors")
    public R<List<Map<String, Object>>> tutors() {
        return R.ok(studentService.tutors());
    }

    @GetMapping("/application")
    public R<Map<String, Object>> application() {
        return R.ok(studentService.myApplication());
    }

    @PutMapping("/application")
    public R<Void> submit(@Valid @RequestBody ApplyReq req) {
        studentService.submit(req);
        return R.ok();
    }

    @GetMapping("/offers")
    public R<List<Map<String, Object>>> offers() {
        return R.ok(studentService.myOffers());
    }

    @PostMapping("/offers/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        studentService.confirm(id);
        return R.ok();
    }

    @PostMapping("/offers/{id}/decline")
    public R<Void> decline(@PathVariable Long id) {
        studentService.decline(id);
        return R.ok();
    }

    @GetMapping("/result")
    public R<Map<String, Object>> result() {
        return R.ok(studentService.myResult());
    }
}
