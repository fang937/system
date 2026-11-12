package com.hbsd.labselect.ai;

import com.hbsd.labselect.common.BizException;

import java.util.List;
import java.util.Map;

/**
 * AI 能力网关 —— 唯一的外部模型接入点。
 *
 * <p>约束：本接口只接收已经组装好的业务数据（Map），不直接访问数据库，
 * 也不修改业务状态；所有落库与鉴权都在 AiService 中完成。
 * 这样替换模型供应商时不会牵连业务逻辑。</p>
 *
 * <p>四类能力与入参约定见 docs/AI集成说明.md：</p>
 * <ul>
 *   <li>interviewTips：student（学生档案）、tutors（可报导师）、rubric（评分标准）</li>
 *   <li>recommend：student、tutors、rubric</li>
 *   <li>resumeReview：student、application、tutor、rubric</li>
 *   <li>avatarChat：persona（分身设定）、tutor（导师资料）、question、history</li>
 * </ul>
 */
public interface AiGateway {

    /** 供应商标识，写入调用记录，如 mock / openai / qwen */
    String provider();

    /** 实际使用的模型名 */
    String model();

    /** 学生端：面试技巧分析 */
    Map<String, Object> interviewTips(Map<String, Object> input);

    /** 学生端：按技能推荐实训室 / 导师 */
    Map<String, Object> recommend(Map<String, Object> input);

    /** 教师端：简历初评 + 多维评分建议 */
    Map<String, Object> resumeReview(Map<String, Object> input);

    /** 学生端：向导师 AI 分身提问 */
    Map<String, Object> avatarChat(Map<String, Object> input);

    /** 本次实际承担调用的通道名（多通道时用于留痕与前端展示） */
    default String activeName() {
        return provider();
    }

    /** 通道状态（单通道实现只返回自己） */
    default List<Map<String, Object>> providers() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("name", provider());
        row.put("model", model());
        row.put("configured", true);
        row.put("available", true);
        row.put("quotaExceeded", false);
        row.put("active", true);
        return List.of(row);
    }

    /** 手动切换首选通道（仅多通道实现支持） */
    default void switchProvider(String name) {
        throw new BizException(400, "当前为单通道模式，无可切换的通道");
    }

    /** 供实现类复用：把任意对象安全转为字符串列表 */
    @SuppressWarnings("unchecked")
    static List<String> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
