package com.hbsd.labselect.dto;

import java.time.LocalDateTime;

/** 系统管理员调整阶段时间窗口。 */
public record PhaseWindowReq(LocalDateTime startAt, LocalDateTime endAt) {
}
