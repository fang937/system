package com.hbsd.labselect.controller;

import com.hbsd.labselect.ai.AiService;
import com.hbsd.labselect.common.R;
import com.hbsd.labselect.common.RequireRole;
import com.hbsd.labselect.dto.AiChatReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 能力接口。当前默认走 mock（规则引擎），返回结构与真实模型保持一致；
 * 接入真实模型只替换 AiGateway 实现，接口契约不变。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** 评分标准：所有端共用 */
    @GetMapping("/rubric")
    public R<List<Map<String, Object>>> rubric() {
        return R.ok(aiService.rubric());
    }

    /** 学生端：面试技巧分析 */
    @PostMapping("/interview-tips")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> interviewTips() {
        return R.ok(aiService.interviewTips());
    }

    /** 学生端：按技能推荐实训室 / 导师 */
    @GetMapping("/recommendations")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> recommendations() {
        return R.ok(aiService.recommendations());
    }

    /** 学生端：可咨询的导师分身列表 */
    @GetMapping("/avatars")
    @RequireRole("STUDENT")
    public R<List<Map<String, Object>>> avatars() {
        return R.ok(aiService.avatars());
    }

    /** 学生端：向导师分身提问 */
    @PostMapping("/avatars/{tutorId}/chat")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> chat(@PathVariable Long tutorId, @Valid @RequestBody AiChatReq req) {
        return R.ok(aiService.chat(tutorId, req.question(), req.history()));
    }

    /** 教师端：简历初评 + 多维评分建议 */
    @PostMapping("/resume-review")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Map<String, Object>> resumeReview(@RequestBody Map<String, Object> body) {
        Object value = body.get("applicationId");
        if (value == null) {
            return R.fail(400, "缺少 applicationId");
        }
        return R.ok(aiService.resumeReview(Long.parseLong(String.valueOf(value))));
    }

    /** 管理员：查看所有 AI 通道状态（额度、冷却、最近错误） */
    @GetMapping("/providers")
    @RequireRole("SYS_ADMIN")
    public R<List<Map<String, Object>>> providers() {
        return R.ok(aiService.providers());
    }

    /** 管理员：手动切换首选通道（同时清除该通道的额度冷却） */
    @PostMapping("/providers/{name}/activate")
    @RequireRole("SYS_ADMIN")
    public R<Void> activateProvider(@PathVariable String name) {
        aiService.switchProvider(name);
        return R.ok();
    }

    /** 管理员：查看分身身份设定（未自定义时返回按导师资料推导的默认值） */
    @GetMapping("/personas/{tutorId}")
    @RequireRole("SYS_ADMIN")
    public R<Map<String, Object>> persona(@PathVariable Long tutorId) {
        return R.ok(aiService.personaDetail(tutorId));
    }

    /** 管理员：维护分身身份设定 */
    @PutMapping("/personas/{tutorId}")
    @RequireRole("SYS_ADMIN")
    public R<Void> savePersona(@PathVariable Long tutorId, @RequestBody Map<String, Object> body) {
        aiService.savePersona(tutorId, body);
        return R.ok();
    }
}
