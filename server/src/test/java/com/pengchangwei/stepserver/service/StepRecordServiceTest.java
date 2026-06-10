package com.pengchangwei.stepserver.service;

import com.pengchangwei.stepserver.dto.StepUploadRequest;
import com.pengchangwei.stepserver.entity.StepRecord;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepRecordServiceTest {

    @Mock
    private StepRecordMapper stepRecordMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private StepRecordService stepRecordService;
    private final Long userId = 1L;
    private final LocalDate today = LocalDate.of(2025, 6, 5);

    @BeforeEach
    void setUp() {
        stepRecordService = new StepRecordService(stepRecordMapper, redisTemplate);
        LoginUser.set(userId);
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    /** 上报：同一天已有记录，走更新逻辑，并同步写 Redis 排行榜 */
    @Test
    void upload_whenExistingRecord_shouldUpdateAndSyncRedis() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        StepUploadRequest request = new StepUploadRequest();
        request.setStepDate(today);
        request.setStepCount(5000);

        StepRecord existing = new StepRecord();
        existing.setId(1L);
        existing.setUserId(userId);
        existing.setStepDate(today);
        existing.setStepCount(3000);

        when(stepRecordMapper.findByUserIdAndDate(userId, today)).thenReturn(existing);

        String result = stepRecordService.upload(request);

        assertEquals("上报成功", result);
        assertEquals(5000, existing.getStepCount());
        verify(stepRecordMapper).updateById(existing);
        verify(stepRecordMapper, never()).insert(any());

        verify(zSetOperations).add(eq("ranking:2025-06-05"), eq("1"), eq(5000.0));
        verify(redisTemplate).expire(eq("ranking:2025-06-05"), eq(48L), eq(TimeUnit.HOURS));
    }

    /** 上报：当天无记录，走新增逻辑，并同步写 Redis 排行榜 */
    @Test
    void upload_whenNoRecord_shouldInsertAndSyncRedis() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        StepUploadRequest request = new StepUploadRequest();
        request.setStepDate(today);
        request.setStepCount(8000);

        when(stepRecordMapper.findByUserIdAndDate(userId, today)).thenReturn(null);

        String result = stepRecordService.upload(request);

        assertEquals("上报成功", result);
        verify(stepRecordMapper).insert(any(StepRecord.class));
        verify(stepRecordMapper, never()).updateById(any());

        verify(zSetOperations).add(eq("ranking:2025-06-05"), eq("1"), eq(8000.0));
        verify(redisTemplate).expire(eq("ranking:2025-06-05"), eq(48L), eq(TimeUnit.HOURS));
    }

    /** 上报：Redis 写入失败不影响主流程，上传仍然成功 */
    @Test
    void upload_whenRedisFails_shouldStillUploadSuccessfully() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis连接超时"));

        StepUploadRequest request = new StepUploadRequest();
        request.setStepDate(today);
        request.setStepCount(5000);

        StepRecord existing = new StepRecord();
        existing.setId(1L);
        existing.setUserId(userId);
        existing.setStepDate(today);
        existing.setStepCount(3000);

        when(stepRecordMapper.findByUserIdAndDate(userId, today)).thenReturn(existing);

        String result = stepRecordService.upload(request);

        assertEquals("上报成功", result);
        verify(stepRecordMapper).updateById(existing);
    }

    /** 查当天有步数时返回实际数据和公里数 */
    @Test
    void getDaily_whenRecordExists_shouldReturnStepsAndDistance() {
        StepRecord record = new StepRecord();
        record.setStepCount(10000);
        when(stepRecordMapper.findByUserIdAndDate(userId, today)).thenReturn(record);

        Map<String, Object> result = stepRecordService.getDaily(today);

        assertEquals(today, result.get("date"));
        assertEquals(10000, result.get("stepCount"));
        assertEquals(7.0, result.get("distance"));
    }

    /** 查当天无步数时返回0 */
    @Test
    void getDaily_whenNoRecord_shouldReturnZero() {
        when(stepRecordMapper.findByUserIdAndDate(userId, today)).thenReturn(null);

        Map<String, Object> result = stepRecordService.getDaily(today);

        assertEquals(0, result.get("stepCount"));
        assertEquals(0.0, result.get("distance"));
    }

    /** 周查询 → 返回周一至今每天数据，无记录填0 */
    @Test
    void getWeekly_shouldReturnWeekDateMap() {
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        StepRecord record = new StepRecord();
        record.setStepDate(today);
        record.setStepCount(5000);
        when(stepRecordMapper.findByUserIdAndDateRange(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(record));

        Map<LocalDate, Map<String, Object>> result = stepRecordService.getWeekly(monday, today);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
