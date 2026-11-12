package com.hbsd.labselect.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 阶段 4：面试成绩录入。 */
public record ScoreReq(
        @NotNull(message = "请填写面试成绩") @DecimalMin(value = "0", message = "成绩不能小于 0")
        @DecimalMax(value = "100", message = "成绩不能大于 100") BigDecimal score,
        String scoreDetail,
        String remark) {
}
