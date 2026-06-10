package com.pengchangwei.stepserver.controller;

import com.pengchangwei.stepserver.annotation.RateLimit;
import com.pengchangwei.stepserver.common.Result;
import com.pengchangwei.stepserver.dto.StepUploadRequest;
import com.pengchangwei.stepserver.service.StepRecordService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/step")
public class StepController {

    private final StepRecordService stepRecordService;

    public StepController(StepRecordService stepRecordService) {
        this.stepRecordService = stepRecordService;
    }

    /**
     * 步数上报，客户端定时把当天步数同步上来，同一天重复上报会覆盖。
     * 限制每用户每60秒最多10次上报，防止恶意刷接口。
     */
    @RateLimit(key = "step_upload", maxCount = 10, windowSeconds = 60)
    @PostMapping("/upload")
    public Result<String> upload(@Valid @RequestBody StepUploadRequest request) {
        return Result.ok(stepRecordService.upload(request));
    }

    /**
     * 查某一天的步数和里程，不传日期默认查今天。
     */
    @GetMapping("/daily")
    public Result<Map<String, Object>> getDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return Result.ok(stepRecordService.getDaily(date));
    }

    /**
     * 本周步数，周一到今天，没计步的天返回0。
     */
    @GetMapping("/weekly")
    public Result<Map<LocalDate, Map<String, Object>>> getWeekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(stepRecordService.getWeekly(startDate, endDate));
    }

    /**
     * 本月步数，1号到今天，没计步的天返回0。
     */
    @GetMapping("/monthly")
    public Result<Map<LocalDate, Map<String, Object>>> getMonthly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(stepRecordService.getMonthly(startDate, endDate));
    }
}
