package com.hbsd.labselect.dto;

import java.math.BigDecimal;

/**
 * 导师新增/编辑（同时维护对应登录账号）。
 * labLeader 为 true 时，该导师同时被授予「实训室负责人」身份（role=LAB_ADMIN）。
 */
public record TutorSaveReq(
        Long id,
        String username,
        String name,
        Long labId,
        String title,
        String researchDirection,
        String intro,
        Integer quota,
        BigDecimal priorityScore,
        Boolean labLeader) {
}
