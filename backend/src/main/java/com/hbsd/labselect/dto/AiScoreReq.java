package com.hbsd.labselect.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多维面试评分。code 对应 score_rubrics.code，score 为 0-100，
 * 后端按维度权重折算总分写入 interview_results.score。
 */
public record AiScoreReq(List<Item> items) {

    public record Item(String code, BigDecimal score, String comment) {
    }
}
