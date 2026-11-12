package com.hbsd.labselect.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 分端登录：portal 决定账号必须属于哪一端，账号与入口不匹配时后端直接拒绝。
 * STUDENT = 学生端；TUTOR = 导师端（含实训室负责人）；ADMIN = 管理员端。
 */
public record LoginReq(
        @NotBlank(message = "请输入账号") String username,
        @NotBlank(message = "请输入密码") String password,
        @NotBlank(message = "请选择登录入口") String portal) {
}
