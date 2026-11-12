package com.hbsd.labselect.common;

/**
 * 请求级用户上下文。由 AuthInterceptor 写入，Controller / Service 只读。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public record CurrentUser(Long id, String role, String name, Long labId, Long tutorId) {

        public boolean is(String r) {
            return r.equals(role);
        }

        public boolean isAdmin() {
            return "SYS_ADMIN".equals(role);
        }

        public boolean inWorkbench() {
            return "TUTOR".equals(role) || "LAB_ADMIN".equals(role);
        }
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized();
        }
        return user;
    }

    public static CurrentUser getOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
