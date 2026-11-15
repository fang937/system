package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.entity.SelectionPhase;
import com.hbsd.labselect.entity.SystemConfig;
import com.hbsd.labselect.mapper.SelectionPhaseMapper;
import com.hbsd.labselect.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阶段时间轴服务。
 *
 * 设计要点：后端是时间的唯一权威。所有写接口都要经过 {@link #require(String...)}，
 * 前端隐藏按钮只是体验优化，抓包直接调接口同样会被拒绝。
 */
@Service
@RequiredArgsConstructor
public class PhaseService {

    public static final String BRIEFING = "BRIEFING";
    public static final String OPEN_DAY = "OPEN_DAY";
    public static final String APPLY = "APPLY";
    public static final String INTERVIEW = "INTERVIEW";
    public static final String CONFIRM = "CONFIRM";
    public static final String SUPPLEMENT = "SUPPLEMENT";
    public static final String ADJUST = "ADJUST";
    public static final String PUBLISH = "PUBLISH";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final SelectionPhaseMapper phaseMapper;
    private final SystemConfigMapper configMapper;

    /** 当前轮次，由管理员在阶段配置页切换，默认第 1 轮。 */
    public int currentRound() {
        SystemConfig config = configMapper.selectOne(
                new QueryWrapper<SystemConfig>().eq("config_key", "round.current"));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(config.getConfigValue());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public void setCurrentRound(int round) {
        SystemConfig config = configMapper.selectOne(
                new QueryWrapper<SystemConfig>().eq("config_key", "round.current"));
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey("round.current");
            config.setConfigValue(String.valueOf(round));
            config.setValueType("INT");
            config.setConfigGroup("ROUND");
            config.setDescription("当前进行中的轮次");
            configMapper.insert(config);
        } else {
            config.setConfigValue(String.valueOf(round));
            configMapper.updateById(config);
        }
    }

    /**
     * 把时间轴整体重置到初始阶段（宣讲）。
     *
     * <p>做法：保留每个阶段原有“时长”，从当前时间起按顺序重新排：
     * 第一个阶段立即生效，后续阶段在前一阶段结束后间隔 1 小时依次排开。
     * 这样不会出现旧窗口仍覆盖当前时间、导致多个阶段同时“进行中”的情况。
     * 调用方（{@code AdminService.resetAll}）负责在同一事务里清理志愿与面试数据。</p>
     */
    public void resetTimeline() {
        List<SelectionPhase> phases = list();
        LocalDateTime now = LocalDateTime.now().withNano(0).minusMinutes(1);
        LocalDateTime cursor = now;
        boolean first = true;
        for (SelectionPhase phase : phases) {
            Duration duration = Duration.between(phase.getStartAt(), phase.getEndAt());
            if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(1)) < 0) {
                duration = Duration.ofDays(1);
            }
            LocalDateTime start = first ? now : cursor.plusHours(1);
            LocalDateTime end = start.plus(duration);
            phase.setStartAt(start);
            phase.setEndAt(end);
            phase.setStatus(first ? "ACTIVE" : "PENDING");
            phaseMapper.updateById(phase);
            cursor = end;
            first = false;
        }
        setCurrentRound(1);
    }

    public List<SelectionPhase> list() {
        return phaseMapper.selectList(new QueryWrapper<SelectionPhase>()
                .orderByAsc("seq")
                .orderByAsc("round_no"));
    }

    /**
     * 当前生效阶段：取包含当前时间、业务顺序最大的那一条；
     * 同顺序时优先取轮次更具体的那一条（覆盖全局阶段）。
     */
    public Optional<SelectionPhase> active() {
        LocalDateTime now = LocalDateTime.now();
        return list().stream()
                .filter(p -> !now.isBefore(p.getStartAt()) && !now.isAfter(p.getEndAt()))
                .max(Comparator.comparing(SelectionPhase::getSeq)
                        .thenComparing(p -> p.getRoundNo() == null ? 0 : p.getRoundNo()));
    }

    public Optional<SelectionPhase> find(String code, Integer roundNo) {
        return list().stream()
                .filter(p -> p.getCode().equals(code))
                .filter(p -> roundNo == null ? p.getRoundNo() == null : roundNo.equals(p.getRoundNo()))
                .findFirst();
    }

    /**
     * 校验当前处于指定阶段之一，否则抛出带时间说明的业务异常。
     * 轮次相关阶段（APPLY/INTERVIEW/CONFIRM）自动匹配当前轮次。
     */
    public SelectionPhase require(String... codes) {
        LocalDateTime now = LocalDateTime.now();
        int round = currentRound();
        for (String code : codes) {
            Optional<SelectionPhase> phase = find(code, isRoundScoped(code) ? round : null);
            if (phase.isPresent()) {
                SelectionPhase p = phase.get();
                if (!now.isBefore(p.getStartAt()) && !now.isAfter(p.getEndAt())) {
                    return p;
                }
            }
        }
        String code = codes[0];
        Optional<SelectionPhase> target = find(code, isRoundScoped(code) ? round : null);
        String label = target.map(SelectionPhase::getName).orElse(phaseLabel(code));
        if (target.isPresent()) {
            SelectionPhase p = target.get();
            throw new BizException(String.format("当前不在「%s」阶段（%s），该阶段开放时间为 %s ~ %s",
                    label, roundLabel(code, round), p.getStartAt().format(FMT), p.getEndAt().format(FMT)));
        }
        throw new BizException("当前不在「" + label + "」阶段，操作已拒绝");
    }

    public boolean inWindow(String code) {
        LocalDateTime now = LocalDateTime.now();
        int round = currentRound();
        return find(code, isRoundScoped(code) ? round : null)
                .map(p -> !now.isBefore(p.getStartAt()) && !now.isAfter(p.getEndAt()))
                .orElse(false);
    }

    /** 阶段 5 的确认截止时间：确认阶段结束时间；缺失时退化为当前时间 + 配置小时数。 */
    public LocalDateTime confirmDeadline(int round, int fallbackHours) {
        return find(CONFIRM, round)
                .map(SelectionPhase::getEndAt)
                .orElseGet(() -> LocalDateTime.now().plusHours(fallbackHours));
    }

    public Map<String, Object> phaseMap(SelectionPhase p) {
        if (p == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String state;
        if (now.isBefore(p.getStartAt())) {
            state = "PENDING";
        } else if (now.isAfter(p.getEndAt())) {
            state = "CLOSED";
        } else {
            state = "ACTIVE";
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("code", p.getCode());
        map.put("name", p.getName());
        map.put("seq", p.getSeq());
        map.put("roundNo", p.getRoundNo());
        map.put("startAt", p.getStartAt());
        map.put("endAt", p.getEndAt());
        map.put("state", state);
        map.put("remark", p.getRemark());
        map.put("secondsLeft", Math.max(0, Duration.between(now, p.getEndAt()).getSeconds()));
        return map;
    }

    public Map<String, Object> now() {
        Map<String, Object> map = new LinkedHashMap<>();
        SelectionPhase active = active().orElse(null);
        map.put("round", currentRound());
        map.put("phase", phaseMap(active));
        map.put("phases", list().stream().map(this::phaseMap).toList());
        return map;
    }

    public static boolean isRoundScoped(String code) {
        return APPLY.equals(code) || INTERVIEW.equals(code) || CONFIRM.equals(code);
    }

    public static String phaseLabel(String code) {
        return switch (code) {
            case BRIEFING -> "宣讲";
            case OPEN_DAY -> "开放日";
            case APPLY -> "意向填报";
            case INTERVIEW -> "面试与初选";
            case CONFIRM -> "拟录取确认";
            case SUPPLEMENT -> "分轮补录";
            case ADJUST -> "统筹调剂";
            case PUBLISH -> "结果公示";
            default -> code;
        };
    }

    private String roundLabel(String code, int round) {
        return isRoundScoped(code) ? "第 " + round + " 轮" : "全局阶段";
    }
}
