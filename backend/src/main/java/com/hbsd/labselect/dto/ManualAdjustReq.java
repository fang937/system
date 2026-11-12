package com.hbsd.labselect.dto;

/** 人工兜底调剂（阶段 7 第三优先级）。 */
public record ManualAdjustReq(Long studentId, Long tutorId, String reason) {
}
