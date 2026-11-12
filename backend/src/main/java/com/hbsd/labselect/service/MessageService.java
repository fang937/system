package com.hbsd.labselect.service;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.entity.DirectMessage;
import com.hbsd.labselect.mapper.DirectMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 师生直连私信。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>发送者永远取自登录身份，请求体不接受 senderId，避免冒名发送。</li>
 *   <li>只能「学生 ↔ 导师/实训室负责人」互通：学生可以给任意一位老师发，老师也可以回复任何学生，
 *       但老师之间、学生之间不通，减少无关噪音。</li>
 *   <li>拉取会话时自动把对方发来的未读标记为已读。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Set<String> TEACHER_ROLES = Set.of("TUTOR", "LAB_ADMIN");
    private static final int MAX_CONTENT = 1000;
    private static final int MAX_HISTORY = 200;

    private final DirectMessageMapper messageMapper;
    private final JdbcTemplate jdbc;

    /** 我的会话列表：一人一条，带最后一条消息与未读数，按最后消息时间倒序。 */
    public Map<String, Object> threads() {
        Long me = UserContext.get().id();
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT u.id AS userId, u.name AS userName, u.role AS userRole, l.name AS labName,
                       (SELECT m.content FROM direct_messages m
                         WHERE (m.sender_id = u.id AND m.receiver_id = ?)
                            OR (m.sender_id = ? AND m.receiver_id = u.id)
                         ORDER BY m.id DESC LIMIT 1) AS lastContent,
                       (SELECT m.created_at FROM direct_messages m
                         WHERE (m.sender_id = u.id AND m.receiver_id = ?)
                            OR (m.sender_id = ? AND m.receiver_id = u.id)
                         ORDER BY m.id DESC LIMIT 1) AS lastAt,
                       (SELECT COUNT(*) FROM direct_messages m
                         WHERE m.sender_id = u.id AND m.receiver_id = ? AND m.is_read = 0) AS unreadCount
                  FROM users u
                  JOIN direct_messages dm
                    ON (dm.sender_id = u.id AND dm.receiver_id = ?)
                    OR (dm.sender_id = ? AND dm.receiver_id = u.id)
                  LEFT JOIN labs l ON l.id = u.lab_id
                 GROUP BY u.id, u.name, u.role, l.name
                 ORDER BY lastAt DESC
                """, me, me, me, me, me, me, me);
        Integer unreadTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM direct_messages WHERE receiver_id = ? AND is_read = 0",
                Integer.class, me);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadTotal", unreadTotal == null ? 0 : unreadTotal);
        result.put("threads", list);
        return result;
    }

    /** 与某人的会话历史，读取即把对方发来的未读置为已读。 */
    @Transactional
    public Map<String, Object> conversation(Long peerId) {
        Long me = UserContext.get().id();
        Map<String, Object> peer = peerInfo(me, peerId);
        jdbc.update("UPDATE direct_messages SET is_read = 1 WHERE sender_id = ? AND receiver_id = ? AND is_read = 0",
                peerId, me);
        List<Map<String, Object>> messages = jdbc.queryForList("""
                SELECT m.id, m.sender_id AS senderId, m.receiver_id AS receiverId, m.content,
                       m.is_read AS isRead, m.created_at AS createdAt,
                       CASE WHEN m.sender_id = ? THEN 1 ELSE 0 END AS mine
                  FROM direct_messages m
                 WHERE (m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?)
                 ORDER BY m.id
                 LIMIT ?
                """, me, me, peerId, peerId, me, MAX_HISTORY);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("peer", peer);
        result.put("messages", messages);
        return result;
    }

    /** 发送一条私信，返回落库后的消息。 */
    @Transactional
    public Map<String, Object> send(Long receiverId, String content) {
        Long me = UserContext.get().id();
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException("消息内容不能为空");
        }
        if (text.length() > MAX_CONTENT) {
            throw new BizException("消息最长 " + MAX_CONTENT + " 字");
        }
        if (receiverId == null || receiverId.equals(me)) {
            throw new BizException("不能给自己发消息");
        }
        Map<String, Object> peer = peerInfo(me, receiverId);
        String peerRole = String.valueOf(peer.get("role"));
        String myRole = UserContext.get().role();
        boolean iAmStudent = "STUDENT".equals(myRole);
        boolean iAmTeacher = TEACHER_ROLES.contains(myRole);
        boolean peerIsStudent = "STUDENT".equals(peerRole);
        boolean peerIsTeacher = TEACHER_ROLES.contains(peerRole);
        if (!((iAmStudent && peerIsTeacher) || (iAmTeacher && peerIsStudent))) {
            throw new BizException("私信仅支持学生与导师之间沟通");
        }

        DirectMessage message = new DirectMessage();
        message.setSenderId(me);
        message.setReceiverId(receiverId);
        message.setContent(text);
        message.setIsRead(0);
        messageMapper.insert(message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("senderId", me);
        result.put("receiverId", receiverId);
        result.put("content", text);
        result.put("isRead", 0);
        result.put("createdAt", message.getCreatedAt());
        result.put("mine", 1);
        return result;
    }

    private Map<String, Object> peerInfo(Long me, Long peerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id AS userId, u.name AS userName, u.role AS role, l.name AS labName
                  FROM users u LEFT JOIN labs l ON l.id = u.lab_id
                 WHERE u.id = ? AND u.status = 1
                """, peerId);
        if (rows.isEmpty()) {
            throw new BizException("对方账号不存在或已停用");
        }
        return rows.get(0);
    }
}
