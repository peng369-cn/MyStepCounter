package com.pengchangwei.stepserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pengchangwei.stepserver.entity.StepRecord;
import com.pengchangwei.stepserver.entity.User;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    /** Redis Sorted Set 中一次取出的最大排名条数，超出这个范围走 MySQL 降级 */
    private static final int REDIS_TOP_N = 100;

    private final StepRecordMapper stepRecordMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    public RankingService(StepRecordMapper stepRecordMapper,
                          UserMapper userMapper,
                          StringRedisTemplate redisTemplate) {
        this.stepRecordMapper = stepRecordMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 当天全平台步数排行榜，按步数降序，支持分页。
     * 优先从 Redis Sorted Set 取 Top 100，比 MySQL ORDER BY 全表排序快很多。
     * Redis key 不存在或 Redis 挂了自动降级走 MySQL，保证可用性。
     */
    public Map<String, Object> getDailyRanking(int page, int size) {
        String key = "ranking:" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 请求的排名范围在 Top 100 以内时优先走 Redis Sorted Set
        int maxRank = page * size;
        if (maxRank <= REDIS_TOP_N) {
            try {
                Set<ZSetOperations.TypedTuple<String>> topSet =
                        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, REDIS_TOP_N - 1);
                if (topSet != null && !topSet.isEmpty()) {
                    return buildFromRedisSet(topSet, page, size);
                }
                // key 存在但数据为空（今天还没人上报），正常返回空列表，不降级
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    return buildEmptyPage(page, size);
                }
            } catch (Exception e) {
                log.warn("Redis排行榜查询异常，降级走MySQL: key={}", key, e);
            }
        }

        return getDailyRankingFromMySql(page, size);
    }

    /**
     * 从 ZREVRANGE 返回的 TypedTuple 集合拼装分页结果，昵称批量从 MySQL 补。
     * ZREVRANGE 已按 score 降序排好，member 是 userId，score 是步数。
     */
    private Map<String, Object> buildFromRedisSet(Set<ZSetOperations.TypedTuple<String>> topSet,
                                                  int page, int size) {
        List<Long> userIds = new ArrayList<>();
        List<Integer> stepCounts = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : topSet) {
            if (tuple.getValue() != null) {
                userIds.add(Long.valueOf(tuple.getValue()));
                stepCounts.add(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            }
        }

        Map<Long, String> nicknameMap = batchGetNicknames(userIds);

        int total = userIds.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("userId", userIds.get(i));
            item.put("nickname", nicknameMap.getOrDefault(userIds.get(i), "未知用户"));
            item.put("stepCount", stepCounts.get(i));
            ranking.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", ranking);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** Redis key 存在但 Sorted Set 为空时返回空页，total 为 0 */
    private Map<String, Object> buildEmptyPage(int page, int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("records", new ArrayList<>());
        data.put("total", 0);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 降级方案：纯 MySQL ORDER BY 分页查询当天排行榜。
     * 同时复用为 getDailyRankingNoCache 的后端，供压测对比。
     */
    private Map<String, Object> getDailyRankingFromMySql(int page, int size) {
        Page<StepRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<StepRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StepRecord::getStepDate, LocalDate.now())
                .orderByDesc(StepRecord::getStepCount);
        Page<StepRecord> resultPage = stepRecordMapper.selectPage(pageParam, wrapper);

        List<Long> userIds = resultPage.getRecords().stream()
                .map(StepRecord::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nicknameMap = batchGetNicknames(userIds);

        List<Map<String, Object>> ranking = new ArrayList<>();
        int rank = (page - 1) * size + 1;
        for (StepRecord r : resultPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("userId", r.getUserId());
            item.put("nickname", nicknameMap.getOrDefault(r.getUserId(), "未知用户"));
            item.put("stepCount", r.getStepCount());
            ranking.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", ranking);
        data.put("total", resultPage.getTotal());
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 当天排行榜纯MySQL查询，不走Redis，用于压测对比 Redis Sorted Set 加速效果。
     */
    public Map<String, Object> getDailyRankingNoCache(int page, int size) {
        return getDailyRankingFromMySql(page, size);
    }

    /**
     * 全平台累计总步数排行榜，按用户历史总步数降序，支持分页。
     * 总榜涉及跨天 SUM 聚合，不适合用 Redis Sorted Set，保持 MySQL。
     */
    public Map<String, Object> getTotalRanking(int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = stepRecordMapper.sumAllStepsGroupByUser(size, offset);
        int total = stepRecordMapper.countDistinctUsers();

        List<Long> userIds = rows.stream()
                .map(row -> ((Number) row.get("user_id")).longValue())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nicknameMap = batchGetNicknames(userIds);

        List<Map<String, Object>> ranking = new ArrayList<>();
        int rank = offset + 1;
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("userId", userId);
            item.put("nickname", nicknameMap.getOrDefault(userId, "未知用户"));
            item.put("totalSteps", ((Number) row.get("total_steps")).intValue());
            ranking.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", ranking);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 批量查用户昵称，一次 IN 查询代替 N 次单独查询，减少数据库压力。
     * 三处排行榜逻辑（Redis、MySQL日榜、总榜）共用这个方法。
     */
    private Map<Long, String> batchGetNicknames(List<Long> userIds) {
        Map<Long, String> nicknameMap = new HashMap<>();
        if (userIds.isEmpty()) {
            return nicknameMap;
        }
        List<User> users = userMapper.selectBatchIds(new LinkedHashSet<>(userIds));
        for (User u : users) {
            nicknameMap.put(u.getId(), u.getNickname());
        }
        return nicknameMap;
    }
}
