package com.hbsd.labselect.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.entity.Application;
import com.hbsd.labselect.entity.InterviewResult;
import com.hbsd.labselect.entity.Tutor;
import com.hbsd.labselect.entity.User;
import com.hbsd.labselect.mapper.ApplicationMapper;
import com.hbsd.labselect.mapper.InterviewResultMapper;
import com.hbsd.labselect.mapper.TutorMapper;
import com.hbsd.labselect.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 阶段 4/5 的核心：名额锁定、Offer 发放与确认、超时放弃与自动递补。
 *
 * 名额并发控制一律通过 tutors 表的条件 UPDATE 完成（remaining_quota > 0 才成功），
 * 不做"先查余额再更新"，避免超卖。
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final InterviewResultMapper resultMapper;
    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final ApplicationMapper applicationMapper;
    private final PhaseService phaseService;
    private final NotifyService notifyService;

    @Value("${app.offer.default-confirm-hours:12}")
    private int fallbackHours;

    /**
     * 导师处置某条面试记录。
     * offerStatus: 1 拟录取（占名额发 Offer）/ 2 递补 / 3 淘汰 / 0 撤销回待处理
     */
    @Transactional
    public void decide(Long resultId, int offerStatus, Long tutorId, String remark) {
        InterviewResult result = resultMapper.selectById(resultId);
        if (result == null) {
            throw new BizException("面试记录不存在");
        }
        if (!result.getTutorId().equals(tutorId)) {
            throw BizException.forbidden("只能处置报考自己的学生");
        }
        int previous = result.getOfferStatus() == null ? 0 : result.getOfferStatus();
        if (previous == offerStatus) {
            return;
        }
        if (previous == 1) {
            // 从拟录取状态撤下，先归还名额并尝试递补
            tutorMapper.releaseQuota(result.getTutorId());
        }
        if (offerStatus == 1) {
            int rows = tutorMapper.takeQuota(result.getTutorId());
            if (rows != 1) {
                throw new BizException("名额已用完，无法再发拟录取（请先调整递补或淘汰名单）");
            }
            result.setOfferTime(LocalDateTime.now());
            result.setConfirmDeadline(phaseService.confirmDeadline(result.getRoundNo(), fallbackHours));
            result.setConfirmStatus(0);
            result.setConfirmTime(null);
            notifyOffer(result);
        } else if (offerStatus == 2) {
            result.setReserveRank(nextReserveRank(result.getTutorId(), result.getRoundNo()));
            result.setConfirmStatus(0);
            result.setOfferTime(null);
        } else {
            result.setReserveRank(null);
            result.setConfirmStatus(0);
            result.setOfferTime(null);
        }
        result.setOfferStatus(offerStatus);
        if (remark != null && !remark.isBlank()) {
            result.setInterviewRemark(remark);
        }
        result.setVersion((result.getVersion() == null ? 0 : result.getVersion()) + 1);
        resultMapper.updateById(result);

        if (previous == 1 && offerStatus != 1) {
            promoteReserve(result.getTutorId(), result.getRoundNo());
        }
    }

    /** 学生确认 Offer。一轮只能确认一个，其余 Offer 自动失效并释放名额。 */
    @Transactional
    public void confirm(Long resultId, Long studentId) {
        InterviewResult target = resultMapper.selectById(resultId);
        if (target == null || !target.getStudentId().equals(studentId)) {
            throw new BizException("Offer 不存在或不属于你");
        }
        if (target.getOfferStatus() != 1) {
            throw new BizException("该记录不是待确认的拟录取 Offer");
        }
        if (target.getConfirmStatus() != 0) {
            throw new BizException("该 Offer 已处理，无需重复确认");
        }

        // 同一轮已经确认过其他 Offer，则本轮选择已锁定
        List<InterviewResult> confirmed = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("student_id", studentId)
                .eq("round_no", target.getRoundNo())
                .eq("confirm_status", 1));
        if (!confirmed.isEmpty()) {
            throw new BizException("本轮你已经确认过其他导师的 Offer，不能再次确认");
        }

        LocalDateTime now = LocalDateTime.now();
        target.setConfirmStatus(1);
        target.setConfirmTime(now);
        target.setVersion(target.getVersion() + 1);
        resultMapper.updateById(target);
        markApplicationMatched(target);

        // 该学生的其余拟录取 Offer 一并作废，名额释放给递补
        List<InterviewResult> others = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("student_id", studentId)
                .eq("round_no", target.getRoundNo())
                .eq("offer_status", 1)
                .ne("id", target.getId()));
        for (InterviewResult other : others) {
            if (other.getConfirmStatus() != null && other.getConfirmStatus() == 0) {
                other.setConfirmStatus(3);
                other.setVersion(other.getVersion() + 1);
                resultMapper.updateById(other);
                tutorMapper.releaseQuota(other.getTutorId());
                promoteReserve(other.getTutorId(), other.getRoundNo());
                notifyTutor(other, "学生已选择其他导师，名额已自动释放并顺延给递补");
            }
        }

        notifyService.send(target.getStudentId(), "RESULT", "双选确认成功",
                "你已确认导师，请留意后续公示结果。", "OFFER", target.getId());
        notifyTutor(target, "学生已确认你的拟录取 Offer");
    }

    /** 学生主动放弃 Offer。 */
    @Transactional
    public void decline(Long resultId, Long studentId) {
        InterviewResult result = resultMapper.selectById(resultId);
        if (result == null || !result.getStudentId().equals(studentId)) {
            throw new BizException("Offer 不存在或不属于你");
        }
        if (result.getOfferStatus() != 1 || result.getConfirmStatus() != 0) {
            throw new BizException("该 Offer 当前不可放弃");
        }
        result.setConfirmStatus(3);
        result.setConfirmTime(LocalDateTime.now());
        result.setVersion(result.getVersion() + 1);
        resultMapper.updateById(result);
        tutorMapper.releaseQuota(result.getTutorId());
        promoteReserve(result.getTutorId(), result.getRoundNo());
        notifyTutor(result, "学生主动放弃了你的 Offer，名额已顺延给递补");
    }

    /**
     * 超时未确认处理：到期的待确认 Offer 置为超时放弃 → 释放名额 → 顺延递补 → 通知。
     * 幂等：只有 confirm_status 仍为 0 的记录才会被处理。
     */
    @Transactional
    public int handleTimeouts() {
        List<InterviewResult> expired = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("offer_status", 1)
                .eq("confirm_status", 0)
                .isNotNull("confirm_deadline")
                .lt("confirm_deadline", LocalDateTime.now()));
        int count = 0;
        for (InterviewResult result : expired) {
            result.setConfirmStatus(2);
            result.setVersion(result.getVersion() + 1);
            resultMapper.updateById(result);
            tutorMapper.releaseQuota(result.getTutorId());
            promoteReserve(result.getTutorId(), result.getRoundNo());
            notifyService.send(result.getStudentId(), "TIMEOUT", "Offer 已超时失效",
                    "你在规定时间内未确认拟录取 Offer，系统已视为自动放弃，名额将顺延给递补同学。",
                    "OFFER", result.getId());
            count++;
        }
        return count;
    }

    /** 只收到 1 个 Offer 的学生，在确认阶段开始时由系统自动确认。 */
    @Transactional
    public int autoConfirmSingles() {
        List<InterviewResult> pending = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("offer_status", 1)
                .eq("confirm_status", 0));
        int count = 0;
        for (InterviewResult result : pending) {
            long sameStudent = pending.stream()
                    .filter(r -> r.getStudentId().equals(result.getStudentId())
                            && r.getRoundNo().equals(result.getRoundNo()))
                    .count();
            if (sameStudent != 1) {
                continue;
            }
            result.setConfirmStatus(1);
            result.setConfirmTime(LocalDateTime.now());
            result.setVersion(result.getVersion() + 1);
            resultMapper.updateById(result);
            markApplicationMatched(result);
            notifyService.send(result.getStudentId(), "RESULT", "Offer 已自动确认",
                    "你本轮仅收到 1 个 Offer，系统已按规则自动确认。", "OFFER", result.getId());
            notifyTutor(result, "学生仅有你一个 Offer，系统已自动确认");
            count++;
        }
        return count;
    }

    /** 名额释放后，把该导师顺位最靠前的递补学生转为拟录取。 */
    @Transactional
    public void promoteReserve(Long tutorId, Integer roundNo) {
        List<InterviewResult> reserves = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("tutor_id", tutorId)
                .eq("round_no", roundNo)
                .eq("offer_status", 2)
                .orderByAsc("reserve_rank"));
        for (InterviewResult reserve : reserves) {
            int rows = tutorMapper.takeQuota(tutorId);
            if (rows != 1) {
                return;
            }
            reserve.setOfferStatus(1);
            reserve.setConfirmStatus(0);
            reserve.setOfferTime(LocalDateTime.now());
            reserve.setConfirmDeadline(phaseService.confirmDeadline(roundNo, fallbackHours));
            reserve.setReserveRank(null);
            reserve.setVersion(reserve.getVersion() + 1);
            resultMapper.updateById(reserve);
            notifyService.send(reserve.getStudentId(), "OFFER", "递补成功，收到拟录取 Offer",
                    "你已由递补顺延为拟录取，请在确认阶段内完成确认，逾期视为放弃。", "OFFER", reserve.getId());
            return;
        }
    }

    /** 确认成功后把志愿表标记为已录取，学生端「录取结果」以此为准。 */
    private void markApplicationMatched(InterviewResult result) {
        Application application = applicationMapper.selectOne(new QueryWrapper<Application>()
                .eq("student_id", result.getStudentId())
                .eq("round_no", result.getRoundNo()));
        if (application == null) {
            return;
        }
        application.setFinalTutorId(result.getTutorId());
        application.setStatus("MATCHED");
        applicationMapper.updateById(application);
    }

    private int nextReserveRank(Long tutorId, Integer roundNo) {
        List<InterviewResult> reserves = resultMapper.selectList(new QueryWrapper<InterviewResult>()
                .eq("tutor_id", tutorId)
                .eq("round_no", roundNo)
                .eq("offer_status", 2));
        return reserves.stream()
                .map(InterviewResult::getReserveRank)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private void notifyOffer(InterviewResult result) {
        Tutor tutor = tutorMapper.selectById(result.getTutorId());
        User tutorUser = tutor == null ? null : userMapper.selectById(tutor.getUserId());
        String tutorName = tutorUser == null ? "导师" : tutorUser.getName();
        notifyService.send(result.getStudentId(), "OFFER", "收到拟录取 Offer",
                tutorName + " 向你发放了拟录取 Offer，请在「拟录取确认」阶段内完成确认，逾期视为放弃。",
                "OFFER", result.getId());
    }

    private void notifyTutor(InterviewResult result, String content) {
        Tutor tutor = tutorMapper.selectById(result.getTutorId());
        if (tutor == null) {
            return;
        }
        User student = userMapper.selectById(result.getStudentId());
        notifyService.send(tutor.getUserId(), "OFFER", "学生 Offer 状态变更",
                (student == null ? "学生" : student.getName()) + "：" + content, "OFFER", result.getId());
    }
}
