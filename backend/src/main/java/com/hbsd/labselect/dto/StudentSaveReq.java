package com.hbsd.labselect.dto;

import java.math.BigDecimal;

/**
 * 管理员录入 / 修改学生信息。
 *
 * <p>id 为空表示新增：以学号作为登录账号，初始密码统一为 123456；
 * id 非空表示修改，学号允许调整但必须保持唯一。</p>
 */
public record StudentSaveReq(
        Long id,
        String studentNo,
        String name,
        String major,
        String className,
        String grade,
        BigDecimal gpa,
        String skillTags,
        BigDecimal priorityScore,
        String phone,
        String email
) {
}
