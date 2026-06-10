package com.pengchangwei.stepcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 前台计步服务，监听 TYPE_STEP_COUNTER 传感器，算好的步数写进 Room 数据库，
 * 同时通过 StepCallback 推给 Activity 更新界面。
 */
public class StepCounterService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "step_counter_channel";
    private static final String CHANNEL_NAME = "计步服务";
    private static final int NOTIFICATION_ID = 1;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private SensorManager sensorManager;
    private Sensor stepSensor;

    private SharedPreferences prefs;

    private AppDatabase db;

    private float baselineSteps = 0f;

    private String todayDate;

    private int todaySteps = 0;

    private boolean baselineInitialized = false;

    private volatile int restoredSteps = 0;

    private boolean sensorWasReset = false;

    private final IBinder binder = new StepCounterBinder();

    private StepCallback callback;

    private PowerManager.WakeLock wakeLock;

    private final Handler uploadHandler = new Handler();
    private static final long UPLOAD_INTERVAL = 5 * 60 * 1000;

    public interface StepCallback {
        void onStepChanged(int steps);
    }

    public class StepCounterBinder extends Binder {
        public StepCounterService getService() {
            return StepCounterService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
        prefs = getSharedPreferences("step_data", Context.MODE_PRIVATE);
        db = AppDatabase.getInstance(getApplicationContext());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StepCounter:WakeLock");
            wakeLock.acquire(10 * 60 * 1000);
        }
        startPeriodicUpload();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        setupBaseline();
        registerSensor();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterSensor();
        uploadHandler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
    }

    private void registerSensor() {
        if (stepSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }




    private void setupBaseline() {
        todayDate = SDF.format(new Date());
        String savedDate = prefs.getString("date", "");

        if (!todayDate.equals(savedDate)) {
            baselineSteps = 0f;
            baselineInitialized = false;
            restoredSteps = 0;
            prefs.edit()
                    .putString("date", todayDate)
                    .putFloat("baseline", 0f)
                    .apply();
        } else {
            baselineSteps = prefs.getFloat("baseline", 0f);
            new Thread(() -> {
                StepRecord record = db.stepDao().getRecordByDate(todayDate);
                if (record != null) {
                    restoredSteps = record.getSteps();
                }
            }).start();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float sensorTotal = event.values[0];

            String currentDate = SDF.format(new Date());
            if (StepCalculator.isNewDay(currentDate, todayDate)) {
                todayDate = currentDate;
                baselineSteps = sensorTotal;
                baselineInitialized = true;
                restoredSteps = 0;
                sensorWasReset = false;
                prefs.edit()
                        .putString("date", todayDate)
                        .putFloat("baseline", baselineSteps)
                        .apply();
            }

            if (StepCalculator.isSensorReset(sensorTotal, baselineSteps)) {
                baselineSteps = sensorTotal;
                baselineInitialized = true;
                sensorWasReset = true;
                prefs.edit().putFloat("baseline", baselineSteps).apply();
            }

            if (!baselineInitialized && baselineSteps == 0f) {
                baselineSteps = sensorTotal;
                baselineInitialized = true;
                prefs.edit().putFloat("baseline", baselineSteps).apply();
            }

            int delta = StepCalculator.computeDelta(sensorTotal, baselineSteps);
            todaySteps = StepCalculator.calculateTodaySteps(delta, sensorWasReset, restoredSteps);

            if (callback != null) {
                callback.onStepChanged(todaySteps);
            }

            updateNotification(todaySteps);

            final int steps = todaySteps;
            final String date = todayDate;
            new Thread(() -> db.stepDao().insertOrUpdate(new StepRecord(date, steps))).start();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("计步服务运行中");
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("正在计步")
                .setContentText("今日步数: " + todaySteps)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(int steps) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("正在计步")
                .setContentText("今日步数: " + steps)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    public void setCallback(StepCallback callback) {
        this.callback = callback;
        if (callback != null) {
            callback.onStepChanged(todaySteps);
        }
    }

    public int getTodaySteps() {
        return todaySteps;
    }

    private void startPeriodicUpload() {
        uploadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                uploadSteps();
                uploadHandler.postDelayed(this, UPLOAD_INTERVAL);
            }
        }, UPLOAD_INTERVAL);
    }

    private void uploadSteps() {
        RetrofitClient client = RetrofitClient.getInstance(getApplicationContext());
        if (!client.isLoggedIn() || todaySteps == 0) {
            return;
        }


        Map<String, Object> body = new HashMap<>();
        body.put("stepCount", todaySteps);
        body.put("stepDate", todayDate);

        client.getApiService().uploadSteps(body).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call,
                                   Response<ApiResponse<String>> response) {
                if (!response.isSuccessful()) {
                    Log.e("StepService", "上传失败 HTTP " + response.code());
                } else if (response.body() != null && !response.body().isSuccess()) {
                    Log.e("StepService", "上传业务失败: " + response.body().getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                Log.e("StepService", "上传网络异常: " + t.getMessage());
            }
        });
    }
}
