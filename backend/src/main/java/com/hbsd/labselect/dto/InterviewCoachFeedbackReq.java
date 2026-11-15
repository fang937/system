package com.hbsd.labselect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 面试辅导：提交作答并获取反馈。
 *
 * <p>answer 的长度上限在服务层校验（超长返回 400），这里只做非空与目标岗位长度约束，
 * 便于前端拿到可直接展示的中文提示。</p>
 */
public record InterviewCoachFeedbackReq(
        String section,
        Long tutorId,
        @Size(max = 60, message = "目标岗位不能超过 60 个字") String role,
        @NotBlank(message = "缺少题目，请重新出题") String question,
        @NotBlank(message = "请先写下你的回答") String answer,
        String questionIntent,
        Integer variant) {
}
