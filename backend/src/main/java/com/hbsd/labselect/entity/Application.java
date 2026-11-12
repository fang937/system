package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applications")
public class Application {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Integer roundNo;

    @TableField("tutor_1_id")
    private Long tutor1Id;
    @TableField("tutor_2_id")
    private Long tutor2Id;
    @TableField("tutor_3_id")
    private Long tutor3Id;

    private Integer acceptLabAdjust;
    private Integer acceptGlobalAdjust;
    /** SUBMITTED / MATCHED / REJECTED / EXPIRED */
    private String status;
    private Long finalTutorId;
    private LocalDateTime submittedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
