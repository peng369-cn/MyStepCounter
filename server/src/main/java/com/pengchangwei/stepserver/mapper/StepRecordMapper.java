package com.pengchangwei.stepserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pengchangwei.stepserver.entity.StepRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StepRecordMapper extends BaseMapper<StepRecord> {

    /**
     * 查某个用户某一天的步数记录，用于上报时判断是新增还是更新
     */
    @Select("SELECT * FROM step_record WHERE user_id = #{userId} AND step_date = #{date} AND deleted = 0")
    StepRecord findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 查某个用户一段时间内的步数，按日期升序返回，用于周/月历史查询
     */
    @Select("SELECT * FROM step_record WHERE user_id = #{userId} AND step_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0 ORDER BY step_date ASC")
    List<StepRecord> findByUserIdAndDateRange(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /** 用户累计总步数 */
    @Select("SELECT COALESCE(SUM(step_count), 0) FROM step_record WHERE user_id = #{userId} AND deleted = 0")
    int sumStepsByUserId(@Param("userId") Long userId);

    /** 全平台用户总步数排行，按总步数降序，分页 */
    @Select("SELECT r.user_id, SUM(r.step_count) AS total_steps FROM step_record r WHERE r.deleted = 0 GROUP BY r.user_id ORDER BY total_steps DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> sumAllStepsGroupByUser(@Param("limit") int limit, @Param("offset") int offset);

    /** 参与排行的用户总数 */
    @Select("SELECT COUNT(DISTINCT user_id) FROM step_record WHERE deleted = 0")
    int countDistinctUsers();
}
