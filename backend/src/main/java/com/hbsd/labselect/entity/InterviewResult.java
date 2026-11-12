package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("interview_results")
public class InterviewResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long studentId;
    private Long tutorId;
    private Integer roundNo;
    private BigDecimal score;
    private String scoreDetail;
    /** 0 待处理 / 1 拟录取(已发 Offer) / 2 递补 / 3 淘汰 */
    private Integer offerStatus;
    private Integer reserveRank;
    /** 0 待确认 / 1 已确认 / 2 超时放弃 / 3 学生主动放弃 */
    private Integer confirmStatus;
    private LocalDateTime offerTime;
    private LocalDateTime confirmDeadline;
    private LocalDateTime confirmTime;
    private Long interviewerId;
    private String interviewRemark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
