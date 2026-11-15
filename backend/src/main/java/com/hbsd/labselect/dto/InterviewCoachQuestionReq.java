package com.hbsd.labselect.dto;

import jakarta.validation.constraints.Size;

/**
 * AI 面试辅导：生成一道练习题。
 *
 * @param section 面试环节：self-intro / common / role
 * @param tutorId 目标导师（可选，用于岗位方向相关问题）
 * @param role    目标岗位 / 方向（可选，tutorId 为空时使用）
 * @param variant 换题序号，用于在同一环节内轮换题目
 */
public record InterviewCoachQuestionReq(
        String section,
        Long tutorId,
        @Size(max = 60, message = "目标岗位不能超过 60 个字") String role,
        Integer variant) {
}
