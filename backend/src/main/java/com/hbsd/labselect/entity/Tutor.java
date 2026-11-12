package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tutors")
public class Tutor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long labId;
    private String title;
    private String researchDirection;
    private String intro;
    private Integer quota;
    private Integer remainingQuota;
    private Integer version;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
