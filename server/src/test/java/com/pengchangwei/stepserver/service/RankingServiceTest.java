package com.pengchangwei.stepserver.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pengchangwei.stepserver.entity.StepRecord;
import com.pengchangwei.stepserver.entity.User;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.mapper.UserMapper;
import com.pengchangwei.stepserver.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private StepRecordMapper stepRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(stepRecordMapper, userMapper, redisTemplate);
        LoginUser.set(1L);
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    /**
     * Redis Sorted Set 中有数据时直接取排名，不查步数表，
     * 只批量查用户表补昵称。
     */
    @Test
    void redisHit_shouldReturnRankingWithoutQueryingStepTable() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        tupleSet.add(new DefaultTypedTuple<>("10", 8000.0));
        tupleSet.add(new DefaultTypedTuple<>("20", 5000.0));
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                .thenReturn(tupleSet);

        User u1 = new User();
        u1.setId(10L);
        u1.setNickname("小明");
        User u2 = new User();
        u2.setId(20L);
        u2.setNickname("小红");
        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(u1, u2));

        Map<String, Object> result = rankingService.getDailyRanking(1, 20);

        assertEquals(1, result.get("page"));
        assertEquals(2, result.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(2, records.size());
        assertEquals(1, records.get(0).get("rank"));
        assertEquals(10L, records.get(0).get("userId"));
        assertEquals("小明", records.get(0).get("nickname"));
        assertEquals(8000, records.get(0).get("stepCount"));
        assertEquals(2, records.get(1).get("rank"));
        assertEquals("小红", records.get(1).get("nickname"));
        assertEquals(5000, records.get(1).get("stepCount"));

        verify(stepRecordMapper, never()).selectPage(any(), any());
    }

    /**
     * Redis 抛异常时自动降级走 MySQL，保证排行榜不因 Redis 故障而挂掉。
     * 异常被内部 catch 吃掉，对外正常返回 MySQL 查询结果。
     */
    @Test
    void redisException_shouldFallbackToMySql() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Redis连接超时"));

        StepRecord record = buildRecord(1L, 10L, LocalDate.now(), 5000);
        Page<StepRecord> mockPage = buildPage(Collections.singletonList(record), 1);
        when(stepRecordMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        User user = new User();
        user.setId(10L);
        user.setNickname("小明");
        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.singletonList(user));

        Map<String, Object> result = rankingService.getDailyRanking(1, 20);

        assertEquals(1, result.get("page"));
        assertEquals(1L, result.get("total"));
        verify(stepRecordMapper).selectPage(any(Page.class), any());
    }

    /**
     * Redis key 不存在（比如今天还没人上报过）时降级走 MySQL。
     */
    @Test
    void redisKeyNotExists_shouldFallbackToMySql() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptySet());
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        StepRecord record = buildRecord(1L, 10L, LocalDate.now(), 5000);
        Page<StepRecord> mockPage = buildPage(Collections.singletonList(record), 1);
        when(stepRecordMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        User user = new User();
        user.setId(10L);
        user.setNickname("小明");
        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.singletonList(user));

        Map<String, Object> result = rankingService.getDailyRanking(1, 20);

        assertEquals(1L, result.get("total"));
        verify(stepRecordMapper).selectPage(any(Page.class), any());
    }

    /**
     * 请求的排名范围超出 Redis Top 100 时直接走 MySQL，
     * 连 Redis 都不会去碰，省一次网络往返。
     */
    @Test
    void rankOutOfRedisRange_shouldGoMySqlDirectly() {
        StepRecord r1 = buildRecord(1L, 10L, LocalDate.now(), 8000);
        StepRecord r2 = buildRecord(2L, 20L, LocalDate.now(), 7000);
        Page<StepRecord> mockPage = buildPage(Arrays.asList(r1, r2), 120);
        when(stepRecordMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.emptyList());

        // page=6, size=20 → maxRank=120 > 100，不应调 Redis
        Map<String, Object> result = rankingService.getDailyRanking(6, 20);

        assertEquals(6, result.get("page"));
        assertEquals(120L, result.get("total"));
        verify(redisTemplate, never()).opsForZSet();
    }

    /**
     * 用户在 MySQL 中查不到时昵称兜底为"未知用户"。
     */
    @Test
    void userNotFound_shouldFallbackNicknameToUnknown() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        tupleSet.add(new DefaultTypedTuple<>("99", 1000.0));
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                .thenReturn(tupleSet);

        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = rankingService.getDailyRanking(1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals("未知用户", records.get(0).get("nickname"));
    }

    /**
     * 第二页的排名从 (page-1)*size+1 开始算。
     * Redis 返回 25 条数据，取第 11~20 条，排名从 11 起。
     */
    @Test
    void page2_shouldCalculateRankFromOffset() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        for (int i = 1; i <= 25; i++) {
            tupleSet.add(new DefaultTypedTuple<>(String.valueOf(i * 10), (double) (30000 - i * 1000)));
        }
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                .thenReturn(tupleSet);

        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            User u = new User();
            u.setId((long) (i * 10));
            u.setNickname("用户" + i);
            users.add(u);
        }
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(users);

        Map<String, Object> result = rankingService.getDailyRanking(2, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(10, records.size());
        assertEquals(11, records.get(0).get("rank"));
        assertEquals(20, records.get(9).get("rank"));
    }

    /**
     * 总排行榜走 MySQL 聚合查询，昵称用 batchGetNicknames 批量查。
     */
    @Test
    void totalRanking_shouldReturnPagedTotalSteps() {
        Map<String, Object> row = new HashMap<>();
        row.put("user_id", 10L);
        row.put("total_steps", 5000L);
        when(stepRecordMapper.sumAllStepsGroupByUser(10, 0))
                .thenReturn(Collections.singletonList(row));
        when(stepRecordMapper.countDistinctUsers()).thenReturn(1);

        User user = new User();
        user.setId(10L);
        user.setNickname("小明");
        when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.singletonList(user));

        Map<String, Object> result = rankingService.getTotalRanking(1, 10);

        assertEquals(1, result.get("page"));
        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(1, records.get(0).get("rank"));
        assertEquals("小明", records.get(0).get("nickname"));
        assertEquals(5000, records.get(0).get("totalSteps"));
    }

    private StepRecord buildRecord(long id, long userId, LocalDate date, int steps) {
        StepRecord r = new StepRecord();
        r.setId(id);
        r.setUserId(userId);
        r.setStepDate(date);
        r.setStepCount(steps);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Page<StepRecord> buildPage(List<StepRecord> records, long total) {
        Page<StepRecord> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }
}
