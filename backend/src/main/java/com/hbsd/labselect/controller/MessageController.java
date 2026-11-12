package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.R;
import com.hbsd.labselect.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 师生直连私信。登录即可用（学生 ↔ 导师/负责人），发送者由登录身份决定，不接受前端传 senderId。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/threads")
    public R<Map<String, Object>> threads() {
        return R.ok(messageService.threads());
    }

    @GetMapping("/with/{userId}")
    public R<Map<String, Object>> conversation(@PathVariable Long userId) {
        return R.ok(messageService.conversation(userId));
    }

    @PostMapping("/with/{userId}")
    public R<Map<String, Object>> send(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        Object content = body.get("content");
        if (content == null || String.valueOf(content).isBlank()) {
            throw new BizException("消息内容不能为空");
        }
        return R.ok(messageService.send(userId, String.valueOf(content)));
    }
}
