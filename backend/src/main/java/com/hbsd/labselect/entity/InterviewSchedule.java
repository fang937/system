package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("interview_schedules")
public class InterviewSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long labId;
    private Integer roundNo;
    private Long tutorId;
    private LocalDate interviewDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private Integer capacity;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
