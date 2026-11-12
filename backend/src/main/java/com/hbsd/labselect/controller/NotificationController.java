package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.R;
import com.hbsd.labselect.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三端共用站内通知。 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final StudentService studentService;

    @GetMapping
    public R<Map<String, Object>> list() {
        List<Map<String, Object>> list = studentService.notifications();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("unread", studentService.unreadCount());
        return R.ok(result);
    }

    @GetMapping("/unread-count")
    public R<Integer> unread() {
        return R.ok(studentService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        studentService.readNotification(id);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Void> readAll() {
        studentService.readAll();
        return R.ok();
    }
}
