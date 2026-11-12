package com.hbsd.labselect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbsd.labselect.entity.InterviewResult;
import org.apache.ibatis.annotations.Update;

public interface InterviewResultMapper extends BaseMapper<InterviewResult> {

    /**
     * 超时未确认：把到期的待确认 Offer 标记为超时放弃，返回受影响行数。
     * 由定时任务与"确认阶段结束时"调用，保证幂等。
     */
    @Update("UPDATE interview_results SET confirm_status = 2, version = version + 1 " +
            "WHERE offer_status = 1 AND confirm_status = 0 AND confirm_deadline IS NOT NULL AND confirm_deadline < NOW()")
    int markExpired();
}
