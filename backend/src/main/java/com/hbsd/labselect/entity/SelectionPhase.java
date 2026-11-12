package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段时间轴。code 见 PhaseCode 常量；round_no 为空表示全局阶段。
 */
@Data
@TableName("selection_phases")
public class SelectionPhase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Integer seq;
    private Integer roundNo;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
