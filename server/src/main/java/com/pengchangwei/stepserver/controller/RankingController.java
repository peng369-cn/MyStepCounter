package com.pengchangwei.stepserver.controller;

import com.pengchangwei.stepserver.annotation.RateLimit;
import com.pengchangwei.stepserver.common.Result;
import com.pengchangwei.stepserver.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * 当天全平台步数排行榜，按步数从高到低排，支持分页。
     * 优先走 Redis Sorted Set，Redis 不可用时自动降级 MySQL。
     */
    @RateLimit(key = "ranking_daily", maxCount = 200, windowSeconds = 60)
    @GetMapping("/daily")
    public Result<Map<String, Object>> getDailyRanking(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(rankingService.getDailyRanking(page, size));
    }
    /**
     * 纯MySQL查询排行榜（跳过Redis缓存），仅用于压测对比。
     */
    @RateLimit(key = "ranking_nocache", maxCount = 200, windowSeconds = 60)
    @GetMapping("/no-cache")
    public Result<Map<String, Object>> getDailyRankingNoCache(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(rankingService.getDailyRankingNoCache(page, size));
    }

    /**
     * 全平台累计总步数排行榜，不限当天，统计用户所有历史步数总和。
     */
    @RateLimit(key = "ranking_total", maxCount = 200, windowSeconds = 60)
    @GetMapping("/total")
    public Result<Map<String, Object>> getTotalRanking(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(rankingService.getTotalRanking(page, size));
    }
}
