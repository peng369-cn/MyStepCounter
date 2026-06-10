package com.pengchangwei.stepcounter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * 步数记录的数据库操作接口，Room 编译时自动生成实现类。
 */
@Dao
public interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(StepRecord record);

    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT :limit")
    List<StepRecord> getRecentRecords(int limit);

    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    StepRecord getRecordByDate(String date);

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    List<StepRecord> getRecordsBetween(String startDate, String endDate);
    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<StepRecord> getRecordsBetweenDesc(String startDate, String endDate);

    @Query("SELECT COUNT(*) FROM daily_steps WHERE date < :startDate AND steps > 0")
    int countRecordsBefore(String startDate);

    @Query("SELECT * FROM daily_steps ORDER BY date DESC")
    LiveData<List<StepRecord>> getAllRecordsLiveData();
}
