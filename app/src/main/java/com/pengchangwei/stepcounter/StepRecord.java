package com.pengchangwei.stepcounter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 每日步数实体，映射到 daily_steps 表，
 * date 是主键，保证每天只有一条记录。
 */
@Entity(tableName = "daily_steps")
public class StepRecord {

    @PrimaryKey
    @NonNull
    private String date;

    private int steps;

    public StepRecord(@NonNull String date, int steps) {
        this.date = date;
        this.steps = steps;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }
}
