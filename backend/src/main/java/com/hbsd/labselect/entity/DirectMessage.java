package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 师生直连私信。
 *
 * <p>与 {@code notifications} 的区别：通知是系统单向广播，私信是学生与导师之间的双向会话，
 * 带已读语义与历史记录。收发双方都必须是真实账号，发送者永远取自登录身份。</p>
 */
@Data
@TableName("direct_messages")
public class DirectMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    /** 0未读 1已读（相对收件人） */
    private Integer isRead;
    private LocalDateTime createdAt;
}
