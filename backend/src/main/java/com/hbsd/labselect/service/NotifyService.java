package com.hbsd.labselect.service;

import com.hbsd.labselect.entity.Notification;
import com.hbsd.labselect.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotificationMapper notificationMapper;

    public void send(Long userId, String type, String title, String content, String bizType, Long bizId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setBizType(bizType);
        n.setBizId(bizId);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }
}
