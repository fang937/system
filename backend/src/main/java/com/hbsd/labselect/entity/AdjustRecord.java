package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("adjust_records")
public class AdjustRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer roundNo;
    private Long studentId;
    private Long fromLabId;
    private Long toLabId;
    private Long toTutorId;
    /** 1 实训室内 / 2 全院跨室 / 3 人工兜底 */
    private Integer adjustType;
    private BigDecimal priorityScore;
    private Integer status;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
