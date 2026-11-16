package com.hbsd.labselect.controller;

import com.hbsd.labselect.ai.AiService;
import com.hbsd.labselect.common.R;
import com.hbsd.labselect.common.RequireRole;
import com.hbsd.labselect.dto.AiChatReq;
import com.hbsd.labselect.dto.InterviewCoachFeedbackReq;
import com.hbsd.labselect.dto.InterviewCoachQuestionReq;
import com.hbsd.labselect.dto.ResumeOptimizeReq;
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

    /* ---- 学生端：AI 优化简历 / AI 面试辅导 ---- */

    /** 学生端：逐栏优化简历（正文取自已保存的结构化草稿） */
    @PostMapping("/resume-optimize")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> resumeOptimize(@Valid @RequestBody(required = false) ResumeOptimizeReq req) {
        return R.ok(aiService.resumeOptimize(req));
    }

    /** 学生端：面试辅导出题（自我介绍 / 常见问题 / 岗位方向相关） */
    @PostMapping("/interview-coach/question")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> coachQuestion(@Valid @RequestBody InterviewCoachQuestionReq req) {
        return R.ok(aiService.interviewCoachQuestion(req));
    }

    /** 学生端：提交作答，获取评分维度、亮点、改进与参考表达 */
    @PostMapping("/interview-coach/feedback")
    @RequireRole("STUDENT")
    public R<Map<String, Object>> coachFeedback(@Valid @RequestBody InterviewCoachFeedbackReq req) {
        return R.ok(aiService.interviewCoachFeedback(req));
    }

    /** 学生端：最近练习记录（仅本人） */
    @GetMapping("/interview-coach/history")
    @RequireRole("STUDENT")
    public R<List<Map<String, Object>>> coachHistory() {
        return R.ok(aiService.interviewCoachHistory());
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

    /* ---- 导师端：管理自己的 AI 分身 ---- */

    /** 导师：查看自己分身的接入状态（通道 / 模型 / 是否走规则引擎）与身份设定 */
    @GetMapping("/my-avatar")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Map<String, Object>> myAvatar() {
        return R.ok(aiService.myAvatar());
    }

    /** 导师：保存自己的分身设定（语气、问候语、知识库、边界），只作用于本人 */
    @PutMapping("/my-avatar")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Void> saveMyAvatar(@RequestBody Map<String, Object> body) {
        aiService.saveMyPersona(body);
        return R.ok();
    }

    /** 导师：用自己的分身试聊一次，走与学生提问完全相同的网关链路 */
    @PostMapping("/my-avatar/chat")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Map<String, Object>> chatMyAvatar(@RequestBody Map<String, String> body) {
        return R.ok(aiService.testMyAvatarChat(body.get("question")));
    }

    /* ---- 学生 ↔ 导师分身 / 真人的会话 ---- */

    /** 学生：自己与某位导师分身（含老师真人回复）的会话记录 */
    @GetMapping("/avatars/{tutorId}/messages")
    @RequireRole("STUDENT")
    public R<List<Map<String, Object>>> avatarMessages(@PathVariable Long tutorId) {
        return R.ok(aiService.avatarMessages(tutorId));
    }

    /** 导师：找过自己分身的学生列表（带最后一条消息与未读数） */
    @GetMapping("/my-avatar/conversations")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<List<Map<String, Object>>> myAvatarConversations() {
        return R.ok(aiService.myAvatarConversations());
    }

    /** 导师：与某位学生的完整对话，打开即标记学生消息为已读 */
    @GetMapping("/my-avatar/conversations/{studentId}")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Map<String, Object>> myAvatarConversation(@PathVariable Long studentId) {
        return R.ok(aiService.myAvatarConversation(studentId));
    }

    /** 导师：以真人身份回复，与学生提问、AI 回答在同一线程 */
    @PostMapping("/my-avatar/conversations/{studentId}/reply")
    @RequireRole({"TUTOR", "LAB_ADMIN"})
    public R<Map<String, Object>> replyToStudent(@PathVariable Long studentId,
                                                 @RequestBody Map<String, String> body) {
        return R.ok(aiService.replyToStudent(studentId, body.get("content")));
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

    /** 管理员：拉取该通道 API Key 下的可用模型列表（用于模型下拉选择） */
    @GetMapping("/providers/{name}/models")
    @RequireRole("SYS_ADMIN")
    public R<List<String>> providerModels(@PathVariable String name) {
        return R.ok(aiService.providerModels(name));
    }

    /** 管理员：调试该通道——用当前 Key 与模型发一次最小对话，验证连通性 */
    @PostMapping("/providers/{name}/test")
    @RequireRole("SYS_ADMIN")
    public R<Map<String, Object>> testProvider(@PathVariable String name) {
        return R.ok(aiService.testProvider(name));
    }

    /** 管理员：切换该通道使用的模型，立即生效并持久化（重启后保留） */
    @PutMapping("/providers/{name}/model")
    @RequireRole("SYS_ADMIN")
    public R<Void> setProviderModel(@PathVariable String name, @RequestBody Map<String, String> body) {
        aiService.selectModel(name, body.get("model"));
        return R.ok();
    }

    /** 管理员：写入该通道的 API Key，立即生效并持久化（优先于 .env） */
    @PutMapping("/providers/{name}/key")
    @RequireRole("SYS_ADMIN")
    public R<Void> setProviderKey(@PathVariable String name, @RequestBody Map<String, String> body) {
        aiService.selectApiKey(name, body.get("apiKey"));
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
