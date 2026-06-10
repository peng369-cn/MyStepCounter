package com.pengchangwei.stepcounter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import android.util.Log;

/**
 * 管理计步数据和 UI 状态的 ViewModel，
 * 屏幕旋转时数据不丢，自动处理生命周期。
 *
 * 认证失效处理：各 Activity 拥有独立的 StepViewModel 实例，
 * authFailed 事件不做跨 Activity 传播，谁触发谁处理。
 * 当 OkHttp TokenAuthenticator 刷新 Token 也失效时，401 响应
 * 会透传到 Retrofit 回调的 onResponse，此时触发 authFailed。
 */
public class StepViewModel extends AndroidViewModel {

    private static final String TAG = "StepVM";

    private final AppDatabase db;
    private final SharedPreferences prefs;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static final int MODE_WEEK = 0;
    public static final int MODE_MONTH = 1;

    private int dailyGoal = 8000;
    private int currentMode = MODE_WEEK;
    private Calendar referenceCalendar = Calendar.getInstance();

    private final MutableLiveData<Integer> todaySteps = new MutableLiveData<>();
    private final MutableLiveData<List<StepRecord>> chartRecords = new MutableLiveData<>();
    private final MutableLiveData<List<StepRecord>> listRecords = new MutableLiveData<>();
    private final MutableLiveData<String> dateRangeText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canGoNext = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canGoPrev = new MutableLiveData<>();
    private final MutableLiveData<List<RankingItem>> rankingList = new MutableLiveData<>();

    /**
     * 认证失效事件，任一接口返回 401 且 Token 刷新也失败时触发。
     * 各 Activity 各自 observe，触发后清 Token、跳转登录页。
     * onFailure（网络超时等）不触发此事件，因为 401 只出现在 onResponse。
     */
    private final MutableLiveData<Boolean> authFailed = new MutableLiveData<>();

    private volatile boolean writingToRoom = false;

    private long lastDbTriggerTime = 0;

    private final Observer<List<StepRecord>> dbObserver = new Observer<List<StepRecord>>() {
        @Override
        public void onChanged(List<StepRecord> records) {
            if (writingToRoom) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastDbTriggerTime < 5000) {
                return;
            }
            lastDbTriggerTime = now;
            loadHistoryForRange();
        }
    };

    public StepViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        prefs = application.getSharedPreferences("step_data", Context.MODE_PRIVATE);
        dailyGoal = prefs.getInt("daily_goal", 8000);
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                db.stepDao().getAllRecordsLiveData().observeForever(dbObserver);
            });
        } catch (RuntimeException e) {
            // 无 Looper 环境，observeForever 跳过由 loadData 手动驱动
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        db.stepDao().getAllRecordsLiveData().removeObserver(dbObserver);
    }

    public LiveData<Integer> getTodaySteps() { return todaySteps; }
    public LiveData<List<StepRecord>> getChartRecords() { return chartRecords; }
    public LiveData<List<StepRecord>> getListRecords() { return listRecords; }
    public LiveData<String> getDateRangeText() { return dateRangeText; }
    public LiveData<Boolean> getCanGoNext() { return canGoNext; }
    public LiveData<Boolean> getCanGoPrev() { return canGoPrev; }
    public LiveData<List<RankingItem>> getRankingList() { return rankingList; }
    public LiveData<Boolean> getAuthFailed() { return authFailed; }
    public int getDailyGoal() { return dailyGoal; }
    public int getCurrentMode() { return currentMode; }

    public void loadData() {
        loadHistoryForRange();
        loadRanking();
    }

    public void switchMode(int mode) {
        currentMode = mode;
        referenceCalendar = Calendar.getInstance();
        loadHistoryForRange();
    }

    public void navigatePage(int direction) {
        if (direction > 0) {
            String[] currentRange = (currentMode == MODE_WEEK) ? getWeekRange() : getMonthRange();
            String today = SDF.format(new Date());
            if (currentRange[1].compareTo(today) >= 0) {
                return;
            }
        }
        if (currentMode == MODE_WEEK) {
            referenceCalendar.add(Calendar.WEEK_OF_YEAR, direction);
        } else {
            referenceCalendar.add(Calendar.MONTH, direction);
        }
        loadHistoryForRange();
    }

    public void setDailyGoal(int goal) {
        dailyGoal = goal;
        prefs.edit().putInt("daily_goal", goal).apply();
    }

    /**
     * 根据当前模式（周/月）计算日期范围，当前时间段优先从云端加载，
     * 历史时间段直接读本地 Room 数据库。
     */
    private void loadHistoryForRange() {
        final String[] range;
        if (currentMode == MODE_WEEK) {
            range = getWeekRange();
        } else {
            range = getMonthRange();
        }
        final String startDate = range[0];
        final String endDate = range[1];

        dateRangeText.postValue(formatDateForDisplay(startDate) + " ~ " + formatDateForDisplay(endDate));

        String today = SDF.format(new Date());

        loadFromCloud(range, startDate, endDate, today);
    }

    /** 计算本周一至周日的日期范围 */
    private String[] getWeekRange() {
        Calendar cal = (Calendar) referenceCalendar.clone();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        if (referenceCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
        }
        String startDate = SDF.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 6);
        String endDate = SDF.format(cal.getTime());
        return new String[]{startDate, endDate};
    }

    /** 计算当月 1 号至月底的日期范围 */
    private String[] getMonthRange() {
        Calendar cal = (Calendar) referenceCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String startDate = SDF.format(cal.getTime());
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String endDate = SDF.format(cal.getTime());
        return new String[]{startDate, endDate};
    }

    /** yyyy-MM-dd 转 M月d日 格式用于界面展示 */
    private String formatDateForDisplay(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return month + "月" + day + "日";
        } catch (Exception e) {
            return dateStr;
        }
    }

    /**
     * Room 中可能存在缺失日期（用户当天没走过），
     * 用 0 补齐缺失日期，保证折线图时间轴连续。
     */
    private List<StepRecord> fillMissingDates(List<StepRecord> dbRecords,
                                              String startDate, String endDate) {
        List<StepRecord> result = new ArrayList<>();
        try {
            java.util.Map<String, Integer> dbMap = new java.util.HashMap<>();
            for (StepRecord r : dbRecords) {
                dbMap.put(r.getDate(), r.getSteps());
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(SDF.parse(startDate));
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(SDF.parse(endDate));
            while (!cal.after(endCal)) {
                String dateStr = SDF.format(cal.getTime());
                int steps = dbMap.containsKey(dateStr) ? dbMap.get(dateStr) : 0;
                result.add(new StepRecord(dateStr, steps));
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            return dbRecords;
        }
        return result;
    }

    /** 判断当前查看的周/月是否为本周/本月，决定走云端还是本地 */
    private boolean isCurrentPeriod() {
        Calendar now = Calendar.getInstance();
        if (currentMode == MODE_WEEK) {
            Calendar ref = (Calendar) referenceCalendar.clone();
            ref.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            Calendar nowMonday = (Calendar) now.clone();
            nowMonday.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            return ref.get(Calendar.WEEK_OF_YEAR) == nowMonday.get(Calendar.WEEK_OF_YEAR)
                    && ref.get(Calendar.YEAR) == nowMonday.get(Calendar.YEAR);
        } else {
            return referenceCalendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                    && referenceCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR);
        }
    }

    /**
     * 从后端拉取当前周期步数数据。
     * 成功：展示云端数据，并同步写入 Room 以供历史翻页使用。
     * 401：Token 刷新也失败，说明 refreshToken 已过期或被踢下线，
     *       触发 authFailed 让 Activity 跳转登录页，不降级 Room。
     * 其他失败（网络错误、服务端异常等）：降级读本地 Room 数据。
     * onFailure（连接超时、DNS 失败等）：同样降级 Room，不触发 authFailed。
     */
    private void loadFromCloud(final String[] range, final String startDate,
                               final String endDate, final String today) {
        RetrofitClient client = RetrofitClient.getInstance(getApplication());
        if (!client.isLoggedIn()) {
            loadFromRoom(range, startDate, endDate, today);
            return;
        }

        Call<ApiResponse<Map<String, StepServiceData>>> call;
        if (currentMode == MODE_WEEK) {
            call = client.getApiService().getWeeklySteps(startDate, endDate);
        } else {
            call = client.getApiService().getMonthlySteps(startDate, endDate);
        }

        call.enqueue(new Callback<ApiResponse<Map<String, StepServiceData>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, StepServiceData>>> call,
                                   Response<ApiResponse<Map<String, StepServiceData>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Map<String, StepServiceData> dataMap = response.body().getData();
                    List<StepRecord> records = new ArrayList<>();
                    if (dataMap != null) {
                        for (Map.Entry<String, StepServiceData> entry : dataMap.entrySet()) {
                            String date = entry.getKey();
                            StepServiceData sd = entry.getValue();
                            records.add(new StepRecord(date,
                                    sd != null ? sd.getStepCount() : 0));
                        }
                    }
                    boolean hasCloudData = false;
                    for (StepRecord r : records) {
                        if (r.getSteps() > 0) {
                            hasCloudData = true;
                            break;
                        }
                    }

                    if (hasCloudData) {
                        writingToRoom = true;
                        new Thread(() -> {
                            try {
                                for (StepRecord r : records) {
                                    if (!r.getDate().equals(today) && r.getSteps() > 0) {
                                        db.stepDao().insertOrUpdate(r);
                                    }
                                }
                            } finally {
                                writingToRoom = false;
                            }
                        }).start();
                        applyRecords(fillMissingDates(records, startDate, endDate),
                                startDate, endDate, today);
                    } else {
                        loadFromRoom(range, startDate, endDate, today);
                    }
                } else if (response.code() == 401) {
                    // TokenAuthenticator 刷新失败，认证彻底失效
                    Log.e(TAG, "authFailed触发: loadHistory("
                            + (currentMode == MODE_WEEK ? "周" : "月") + ") 收到401穿透");
                    authFailed.postValue(true);
                } else {
                    // 非 401 的其他服务端错误，降级读本地
                    loadFromRoom(range, startDate, endDate, today);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, StepServiceData>>> call,
                                  Throwable t) {
                // 网络不通，降级读本地，不走 authFailed
                loadFromRoom(range, startDate, endDate, today);
            }
        });
    }

    /** 读本地 Room 数据库的步数记录，子线程操作避免阻塞主线程 */
    private void loadFromRoom(final String[] range, final String startDate,
                              final String endDate, final String today) {
        new Thread(() -> {
            List<StepRecord> dbRecords;
            if (currentMode == MODE_MONTH) {
                dbRecords = db.stepDao().getRecordsBetweenDesc(startDate, endDate);
            } else {
                dbRecords = db.stepDao().getRecordsBetween(startDate, endDate);
            }
            applyRecords(fillMissingDates(dbRecords, startDate, endDate),
                    startDate, endDate, today);
        }).start();
    }

    /**
     * 将补齐后的步数记录分发到各个 LiveData，
     * todaySteps 只更新当日步数，chartRecords 用于折线图，
     * listRecords 倒序排列用于列表展示。
     */
    private void applyRecords(List<StepRecord> filledRecords, String startDate,
                              String endDate, String today) {
        new Thread(() -> {
            StepRecord todayRecord = db.stepDao().getRecordByDate(today);
            int steps = (todayRecord != null) ? todayRecord.getSteps() : 0;

            for (int i = 0; i < filledRecords.size(); i++) {
                if (filledRecords.get(i).getDate().equals(today)) {
                    filledRecords.set(i, new StepRecord(today, steps));
                    break;
                }
            }

            todaySteps.postValue(steps);
            chartRecords.postValue(filledRecords);

            List<StepRecord> reversed = new ArrayList<>(filledRecords);
            Collections.reverse(reversed);
            listRecords.postValue(reversed);

            canGoNext.postValue(endDate.compareTo(today) < 0);
            int countBefore = db.stepDao().countRecordsBefore(startDate);
            canGoPrev.postValue(countBefore > 0);
        }).start();
    }

    /**
     * 从后端拉取今日全平台排行榜。
     * 401 时触发 authFailed，其他失败静默（排行榜非核心数据）。
     * onFailure 不触发 authFailed，因为网络超时不是认证问题。
     */
    public void loadRanking() {
        RetrofitClient client = RetrofitClient.getInstance(getApplication());
        if (!client.isLoggedIn()) {
            return;
        }

        client.getApiService().getDailyRanking().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Map<String, Object> dataMap = response.body().getData();
                    List<Map<String, Object>> rawList = null;
                    if (dataMap != null && dataMap.get("records") instanceof List) {
                        rawList = (List<Map<String, Object>>) dataMap.get("records");
                    }
                    List<RankingItem> items = new ArrayList<>();
                    if (rawList != null) {
                        for (Map<String, Object> map : rawList) {
                            Object rankObj = map.get("rank");
                            Object userIdObj = map.get("userId");
                            Object stepObj = map.get("stepCount");
                            if (!(rankObj instanceof Number) || !(userIdObj instanceof Number)
                                    || !(stepObj instanceof Number)) {
                                continue;
                            }
                            RankingItem item = new RankingItem();
                            item.setRank(((Number) rankObj).intValue());
                            item.setUserId(((Number) userIdObj).longValue());
                            item.setNickname(String.valueOf(map.get("nickname")));
                            item.setStepCount(((Number) stepObj).intValue());
                            items.add(item);
                        }
                    }
                    rankingList.postValue(items);
                } else if (response.code() == 401) {
                    // Token 刷新也失败，认证彻底失效
                    Log.e(TAG, "authFailed触发: loadRanking 收到401穿透");
                    authFailed.postValue(true);
                }
                // 其他失败静默，排行榜非核心数据
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call,
                                  Throwable t) {
                // 网络不通，静默，不触发 authFailed
            }
        });
    }
}
