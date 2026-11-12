package com.hbsd.labselect.common;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 统一鉴权：解析 Token → 写入上下文 → 校验 @RequireRole。
 * 同时把所有 401/403 直接以 JSON 返回，前端不必处理重定向。
 */
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null || token.isBlank()) {
            writeJson(response, 401, "请先登录");
            return false;
        }
        UserContext.CurrentUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            writeJson(response, 401, "登录状态已过期，请重新登录");
            return false;
        }
        UserContext.set(user);

        RequireRole require = method.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require != null && Arrays.stream(require.value()).noneMatch(user.role()::equals)) {
            writeJson(response, 403, "当前账号无权访问该功能");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
