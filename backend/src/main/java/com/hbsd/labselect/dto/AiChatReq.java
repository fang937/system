package com.hbsd.labselect.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/** AI 分身提问。history 为可选的多轮上下文：[{role: "student"|"assistant", content: "..."}] */
public record AiChatReq(
        @NotBlank(message = "请输入问题") String question,
        List<Map<String, String>> history) {
}
