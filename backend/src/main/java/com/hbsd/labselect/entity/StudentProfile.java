package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("student_profiles")
public class StudentProfile {

    @TableId
    private Long userId;
    private String major;
    private String className;
    private String grade;
    private BigDecimal gpa;
    private String skillTags;
    private String resumeUrl;
    private BigDecimal priorityScore;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
