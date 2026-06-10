package com.pengchangwei.stepserver.service;

import com.pengchangwei.stepserver.dto.StepUploadRequest;
import com.pengchangwei.stepserver.entity.StepRecord;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class StepRecordService {

    private static final Logger log = LoggerFactory.getLogger(StepRecordService.class);

    /** 步幅，一步0.7米，公里数 = 步数 × 0.7 ÷ 1000 */
    private static final double STEP_LENGTH = 0.7;

    /** Redis Sorted Set key 过期时间，避免内存无限增长 */
    private static final int RANKING_KEY_TTL_HOURS = 48;

    private final StepRecordMapper stepRecordMapper;
    private final StringRedisTemplate redisTemplate;

    public StepRecordService(StepRecordMapper stepRecordMapper,
                             StringRedisTemplate redisTemplate) {
        this.stepRecordMapper = stepRecordMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 步数上报：同一天有旧记录就覆盖更新，没有就新增。
     * 客户端按定时任务调这个接口，把当天步数同步到云端。
     * MySQL 写入成功后同步更新 Redis Sorted Set 排行榜，
     * Redis 写入失败不影响主流程，仅记日志。
     */
    public String upload(StepUploadRequest request) {
        Long userId = LoginUser.get();
        StepRecord existing = stepRecordMapper.findByUserIdAndDate(userId, request.getStepDate());

        if (existing != null) {
            existing.setStepCount(request.getStepCount());
            stepRecordMapper.updateById(existing);
        } else {
            StepRecord record = new StepRecord();
            record.setUserId(userId);
            record.setStepDate(request.getStepDate());
            record.setStepCount(request.getStepCount());
            stepRecordMapper.insert(record);
        }

        syncToRankingCache(userId, request.getStepDate(), request.getStepCount());
        return "上报成功";
    }

    /**
     * 把步数写入当天 Redis Sorted Set，供排行榜 ZREVRANGE 查询。
     * ZADD 相同 member 会覆盖分数，正好满足同一天重复上报覆盖的需求。
     * 每次写入后重置 key 过期时间为 48 小时，活跃的 key 会自动续期。
     */
    private void syncToRankingCache(Long userId, LocalDate date, int stepCount) {
        try {
            String key = "ranking:" + date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            redisTemplate.opsForZSet().add(key, userId.toString(), stepCount);
            redisTemplate.expire(key, RANKING_KEY_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis排行榜写入失败，降级跳过: userId={}, date={}, stepCount={}", userId, date, stepCount, e);
        }
    }

    /**
     * 查某一天的数据，步数和公里一起返回。公里是实时算的，不存库。
     */
    public Map<String, Object> getDaily(LocalDate date) {
        Long userId = LoginUser.get();
        StepRecord record = stepRecordMapper.findByUserIdAndDate(userId, date);

        Map<String, Object> data = new HashMap<>();
        data.put("date", date);
        if (record != null) {
            data.put("stepCount", record.getStepCount());
            data.put("distance", calcDistance(record.getStepCount()));
        } else {
            data.put("stepCount", 0);
            data.put("distance", 0.0);
        }
        return data;
    }

    /**
     * 查某周每天的步数，不传参数默认本周（周一到今天）。
     */
    public Map<LocalDate, Map<String, Object>> getWeekly(LocalDate startDate, LocalDate endDate) {
        Long userId = LoginUser.get();
        LocalDate today = LocalDate.now();
        if (startDate == null) {
            startDate = today.with(DayOfWeek.MONDAY);
        }
        if (endDate == null) {
            endDate = today;
        }

        List<StepRecord> records = stepRecordMapper.findByUserIdAndDateRange(userId, startDate, endDate);
        return buildDateMap(startDate, endDate, records);
    }

    /**
     * 查某月每天的步数，不传参数默认本月（1号到今天）。
     */
    public Map<LocalDate, Map<String, Object>> getMonthly(LocalDate startDate, LocalDate endDate) {
        Long userId = LoginUser.get();
        LocalDate today = LocalDate.now();
        if (startDate == null) {
            startDate = today.withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = today;
        }

        List<StepRecord> records = stepRecordMapper.findByUserIdAndDateRange(userId, startDate, endDate);
        return buildDateMap(startDate, endDate, records);
    }

    /**
     * 把时间段内每一天拼成一个LinkedHashMap，有记录的填数据，没记录的填0，
     * 保证客户端拿到的日期是连续完整的，画折线图不会断。
     */
    private LinkedHashMap<LocalDate, Map<String, Object>> buildDateMap(
            LocalDate start, LocalDate end, List<StepRecord> records) {

        LinkedHashMap<LocalDate, Map<String, Object>> result = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            result.put(d, null);
        }
        for (StepRecord r : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("stepCount", r.getStepCount());
            item.put("distance", calcDistance(r.getStepCount()));
            result.put(r.getStepDate(), item);
        }
        for (Map.Entry<LocalDate, Map<String, Object>> entry : result.entrySet()) {
            if (entry.getValue() == null) {
                Map<String, Object> zero = new HashMap<>();
                zero.put("stepCount", 0);
                zero.put("distance", 0.0);
                entry.setValue(zero);
            }
        }
        return result;
    }

    /** 步数换算公里，保留两位小数 */
    private double calcDistance(int steps) {
        return Math.round(steps * STEP_LENGTH / 1000.0 * 100.0) / 100.0;
    }
}
