package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.JwtUtil;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.dto.LoginReq;
import com.hbsd.labselect.entity.Lab;
import com.hbsd.labselect.entity.Tutor;
import com.hbsd.labselect.entity.User;
import com.hbsd.labselect.mapper.LabMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import com.hbsd.labselect.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分端登录。四类账号共用一张 users 表，但登录入口与账号身份必须匹配，不能越界：
 *   STUDENT   学生端
 *   TUTOR     导师端（导师事务）
 *   LAB_ADMIN 导师端（导师事务 + 实训室事务，即实训室负责人）
 *   SYS_ADMIN 管理员端
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 登录入口 → 允许进入的账号身份 */
    private static final Map<String, Set<String>> PORTAL_ROLES = Map.of(
            "STUDENT", Set.of("STUDENT"),
            "TUTOR", Set.of("TUTOR", "LAB_ADMIN"),
            "ADMIN", Set.of("SYS_ADMIN"));

    private static final Map<String, String> PORTAL_LABEL = Map.of(
            "STUDENT", "学生端",
            "TUTOR", "导师端",
            "ADMIN", "管理员端");

    private final UserMapper userMapper;
    private final TutorMapper tutorMapper;
    private final LabMapper labMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PhaseService phaseService;

    public Map<String, Object> login(LoginReq req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.username().trim()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(403, "该账号已停用，请联系管理员");
        }
        requirePortalMatch(req.portal(), user.getRole());

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Tutor tutor = tutorMapper.selectOne(new QueryWrapper<Tutor>().eq("user_id", user.getId()));
        Lab lab = user.getLabId() == null ? null : labMapper.selectById(user.getLabId());
        String token = jwtUtil.issue(user.getId(), user.getRole(), user.getName(), user.getLabId(),
                tutor == null ? null : tutor.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", profileOf(user, tutor, lab));
        result.put("phase", phaseService.now());
        return result;
    }

    /** 校验账号身份与登录入口是否匹配，防止跨端登录。 */
    private void requirePortalMatch(String rawPortal, String role) {
        String portal = rawPortal == null ? "" : rawPortal.trim().toUpperCase();
        Set<String> allowed = PORTAL_ROLES.get(portal);
        if (allowed == null) {
            throw new BizException(400, "请选择登录入口");
        }
        if (role == null || role.isBlank() || !allowed.contains(role)) {
            throw new BizException(403, "该账号不属于「" + PORTAL_LABEL.get(portal) + "」，请从对应入口登录");
        }
    }

    public Map<String, Object> me() {
        UserContext.CurrentUser current = UserContext.get();
        User user = userMapper.selectById(current.id());
        if (user == null) {
            throw BizException.unauthorized();
        }
        Tutor tutor = tutorMapper.selectOne(new QueryWrapper<Tutor>().eq("user_id", user.getId()));
        Lab lab = user.getLabId() == null ? null : labMapper.selectById(user.getLabId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", profileOf(user, tutor, lab));
        result.put("phase", phaseService.now());
        return result;
    }

    private Map<String, Object> profileOf(User user, Tutor tutor, Lab lab) {
        List<String> caps = new ArrayList<>();
        switch (user.getRole()) {
            case "STUDENT" -> caps.add("STUDENT");
            case "TUTOR" -> caps.add("TUTOR_WORK");
            case "LAB_ADMIN" -> {
                if (tutor != null) {
                    caps.add("TUTOR_WORK");
                }
                caps.add("LAB_MANAGE");
            }
            case "SYS_ADMIN" -> caps.add("ADMIN");
            default -> {
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("name", user.getName());
        map.put("role", user.getRole());
        map.put("roleLabel", roleLabel(user.getRole()));
        map.put("caps", caps);
        map.put("studentNo", user.getStudentNo());
        map.put("labId", user.getLabId());
        map.put("labName", lab == null ? null : lab.getName());
        map.put("tutorId", tutor == null ? null : tutor.getId());
        map.put("tutorTitle", tutor == null ? null : tutor.getTitle());
        return map;
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case "STUDENT" -> "学生";
            case "TUTOR" -> "导师";
            case "LAB_ADMIN" -> "实训室管理员";
            case "SYS_ADMIN" -> "系统管理员";
            default -> role;
        };
    }
}
