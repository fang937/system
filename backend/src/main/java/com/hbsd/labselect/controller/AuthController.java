package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.R;
import com.hbsd.labselect.dto.LoginReq;
import com.hbsd.labselect.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 统一登录入口：不选角色，登录后由账号识别身份。 */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me());
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        // 无状态 JWT：前端清除 Token 即可
        return R.ok();
    }
}
