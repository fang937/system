package com.hbsd.labselect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbsd.labselect.entity.Tutor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TutorMapper extends BaseMapper<Tutor> {

    /**
     * 原子领取名额：只有 remaining_quota > 0 时才会成功（受影响行数 = 1）。
     * 这是阶段 5 防超卖的核心语句。
     */
    @Update("UPDATE tutors SET remaining_quota = remaining_quota - 1, version = version + 1 " +
            "WHERE id = #{id} AND remaining_quota > 0")
    int takeQuota(@Param("id") Long id);

    /** 释放名额（超时放弃 / 学生放弃 / 取消拟录取）。 */
    @Update("UPDATE tutors SET remaining_quota = remaining_quota + 1, version = version + 1 " +
            "WHERE id = #{id} AND remaining_quota < quota")
    int releaseQuota(@Param("id") Long id);
}
