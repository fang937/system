package com.hbsd.labselect.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 阶段 3：平行志愿填报，最多 3 个，不分先后。 */
public record ApplyReq(
        @NotEmpty(message = "至少填报 1 个志愿") @Size(max = 3, message = "最多填报 3 个志愿") List<Long> tutorIds,
        Boolean acceptLabAdjust,
        Boolean acceptGlobalAdjust) {
}
