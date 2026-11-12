package com.hbsd.labselect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("labs")
public class Lab {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String location;
    private Long leaderId;
    /** 后端/工程技术栈 */
    private String techBackend;
    /** 前端/客户端技术栈 */
    private String techFrontend;
    /** AI/大模型方向 */
    private String aiDirection;
    /** 项目产出类型 */
    private String outputTypes;
    /** 竞赛/科研成果 */
    private String achievements;
    /** 培养模式 */
    private String trainingMode;
    /** 考核/结业标准 */
    private String graduationStandard;
    /** 适合人群 */
    private String suitableFor;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
