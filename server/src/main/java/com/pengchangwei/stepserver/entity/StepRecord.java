package com.pengchangwei.stepserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("step_record")
public class StepRecord {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联user表的用户ID */
    private Long userId;

    /** 步数对应的日期，同一天只保留一条记录 */
    private LocalDate stepDate;

    /** 当天总步数，客户端上报时覆盖更新 */
    private Integer stepCount;

    /** 记录首次写入时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 记录更新时间，每次上报都会刷新 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
