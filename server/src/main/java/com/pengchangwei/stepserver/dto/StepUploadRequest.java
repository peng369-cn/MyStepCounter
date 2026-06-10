package com.pengchangwei.stepserver.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StepUploadRequest {

    /** 当天走的步数，客户端传感器统计后上传 */
    @NotNull(message = "步数不能为空")
    @Min(value = 0, message = "步数不能为负数")
    private Integer stepCount;

    /** 步数对应的日期，通常就是当天 */
    @NotNull(message = "日期不能为空")
    private LocalDate stepDate;
}
