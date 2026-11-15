package com.hbsd.labselect.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 优化简历请求。
 *
 * <p>简历正文以服务端草稿为准（前端在调用前会先保存），这里只传“优化哪几栏、按什么侧重点优化”，
 * 避免同一份内容在请求体与草稿里出现两个版本。</p>
 */
public record ResumeOptimizeReq(
        List<String> sections,
        List<String> focus,
        @Size(max = 60, message = "目标岗位不能超过 60 个字") String targetRole,
        @Size(max = 12, message = "目标关键词最多 12 个")
        List<@Size(max = 30, message = "单个关键词不能超过 30 个字") String> keywords) {
}
