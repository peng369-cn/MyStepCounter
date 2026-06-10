package com.pengchangwei.stepcounter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.view.ViewGroup;
import com.google.android.material.button.MaterialButton;
import android.widget.EditText;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * 计步器主界面，负责 UI 展示和 Service 绑定。
 */
public class MainActivity extends AppCompatActivity
        implements StepCounterService.StepCallback {

    private TextView tvStepCount;
    private TextView tvStepDistance;
    private LinearProgressIndicator progressBar;
    private TextView tvGoal;
    private LineChart lineChart;
    private RecyclerView recyclerHistory;
    private MaterialButton btnWeek;
    private MaterialButton btnMonth;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;
    private TextView tvDateRange;
    private MaterialButton btnSetGoal;

    private StepCounterService stepService;
    private boolean serviceBound = false;

    private StepViewModel viewModel;

    private HistoryAdapter historyAdapter;

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int PERMISSION_NOTIFICATION_CODE = 1002;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            StepCounterService.StepCounterBinder binder =
                    (StepCounterService.StepCounterBinder) iBinder;
            stepService = binder.getService();
            serviceBound = true;
            stepService.setCallback(MainActivity.this);
            updateStepUI(stepService.getTodaySteps());
            viewModel.loadData();
            checkBatteryOptimization();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            stepService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!RetrofitClient.getInstance(this).isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvStepCount = findViewById(R.id.tv_step_count);
        tvStepDistance = findViewById(R.id.tv_step_distance);
        progressBar = findViewById(R.id.progress_bar);
        tvGoal = findViewById(R.id.tv_goal);
        lineChart = findViewById(R.id.line_chart);
        recyclerHistory = findViewById(R.id.recycler_history);
        btnWeek = findViewById(R.id.btn_week);
        btnMonth = findViewById(R.id.btn_month);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        tvDateRange = findViewById(R.id.tv_date_range);
        btnSetGoal = findViewById(R.id.btn_set_goal);

        viewModel = new ViewModelProvider(this).get(StepViewModel.class);

        updateProgress(0);

        viewModel.getTodaySteps().observe(this, steps -> {
            tvStepCount.setText(String.valueOf(steps));
            tvStepDistance.setText(String.format(java.util.Locale.getDefault(), "约 %.2f km", steps * 0.7 / 1000.0));
            updateProgress(steps);
        });
        viewModel.getChartRecords().observe(this, this::refreshChart);
        viewModel.getListRecords().observe(this, records -> historyAdapter.setRecords(records));
        viewModel.getDateRangeText().observe(this, text -> tvDateRange.setText(text));
        viewModel.getCanGoNext().observe(this, enabled -> btnNext.setEnabled(enabled));
        viewModel.getCanGoPrev().observe(this, enabled -> btnPrev.setEnabled(enabled));

        // 认证失效兜底：Token刷新也失败时，清Token、停服务、跳登录页
        viewModel.getAuthFailed().observe(this, isAuthFailed -> {
            if (Boolean.TRUE.equals(isAuthFailed)) {
                RetrofitClient.getInstance(this).clearTokens();
                stopService(new Intent(this, StepCounterService.class));
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        setupChart();

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(new ArrayList<>());
        recyclerHistory.setAdapter(historyAdapter);

        btnWeek.setOnClickListener(v -> switchMode(StepViewModel.MODE_WEEK));
        btnMonth.setOnClickListener(v -> switchMode(StepViewModel.MODE_MONTH));
        btnPrev.setOnClickListener(v -> viewModel.navigatePage(-1));
        btnNext.setOnClickListener(v -> viewModel.navigatePage(1));

        MaterialButton btnRanking = findViewById(R.id.btn_ranking);
        btnRanking.setOnClickListener(v -> startActivity(new Intent(this, RankingActivity.class)));
        MaterialButton btnProfile = findViewById(R.id.btn_profile);
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnSetGoal.setOnClickListener(v -> showSetGoalDialog());

        requestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!serviceBound && hasPermission()) {
            startAndBindService();
        }
        if (serviceBound) {
            viewModel.loadData();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (serviceBound && stepService != null) {
            updateStepUI(stepService.getTodaySteps());
            viewModel.loadData();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACTIVITY_RECOGNITION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }
        startAndBindService();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_NOTIFICATION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAndBindService();
                requestNotificationPermission();
            } else {
                Toast.makeText(this,
                        "需要活动识别权限才能计步", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PERMISSION_NOTIFICATION_CODE) {
            if (grantResults.length == 0 ||
                    grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "通知权限被拒绝，后台计步通知将不显示", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, StepCounterService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStepChanged(int steps) {
        updateStepUI(steps);
    }

    private void updateStepUI(int steps) {
        tvStepCount.setText(String.valueOf(steps));
        tvStepDistance.setText(String.format(java.util.Locale.getDefault(), "约 %.2f km", steps * 0.7 / 1000.0));
        updateProgress(steps);
    }

    private void updateProgress(int steps) {
        int dailyGoal = viewModel.getDailyGoal();
        int percent = (int) (steps * 100f / dailyGoal);
        if (percent > 100) percent = 100;
        progressBar.setProgress(percent);
        tvGoal.setText("目标 " + dailyGoal + " 步 / " + percent + "%");
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setExtraBottomOffset(6f);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#888888"));
        xAxis.setTextSize(11f);
        xAxis.setAxisLineColor(Color.parseColor("#E0E0E0"));
        xAxis.setAxisLineWidth(1f);

        lineChart.getAxisRight().setEnabled(false);

        lineChart.getAxisLeft().setTextColor(Color.parseColor("#AAAAAA"));
        lineChart.getAxisLeft().setTextSize(11f);
        lineChart.getAxisLeft().setAxisLineColor(Color.parseColor("#E0E0E0"));
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#F0F0F0"));
        lineChart.getAxisLeft().setGridLineWidth(0.5f);
        lineChart.getAxisLeft().setDrawZeroLine(false);
    }

    private void refreshChart(List<StepRecord> records) {
        if (records.isEmpty()) {
            lineChart.clear();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            StepRecord r = records.get(i);
            entries.add(new Entry(i, r.getSteps()));
            String shortDate = r.getDate().length() >= 10
                    ? r.getDate().substring(5)
                    : r.getDate();
            dates.add(shortDate);
        }

        LineDataSet dataSet = new LineDataSet(entries, "步数");
        dataSet.setColor(Color.parseColor("#2E7D32"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(Color.parseColor("#2E7D32"));
        dataSet.setCircleRadius(5f);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setCircleHoleRadius(3f);
        dataSet.setDrawCircleHole(true);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        dataSet.setDrawFilled(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            dataSet.setFillDrawable(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.argb(80, 46, 125, 50),
                            Color.argb(10, 46, 125, 50)
                    }));
        }

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dates.size()) {
                    return dates.get(index);
                }
                return "";
            }
        });

        lineChart.invalidate();
        lineChart.animateX(500);
    }

    private void switchMode(int mode) {
        if (mode == StepViewModel.MODE_WEEK) {
            btnWeek.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1B5E20")));
            btnWeek.setTextColor(Color.WHITE);
            btnMonth.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            btnMonth.setTextColor(Color.parseColor("#666666"));
        } else {
            btnMonth.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1B5E20")));
            btnMonth.setTextColor(Color.WHITE);
            btnWeek.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            btnWeek.setTextColor(Color.parseColor("#666666"));
        }

        if (mode == StepViewModel.MODE_MONTH) {
            recyclerHistory.setNestedScrollingEnabled(true);
        } else {
            recyclerHistory.setNestedScrollingEnabled(false);
        }

        viewModel.switchMode(mode);
    }

    private void showSetGoalDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入每日目标步数");
        input.setText(String.valueOf(viewModel.getDailyGoal()));

        new AlertDialog.Builder(this)
                .setTitle("设置每日目标步数")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this,
                                "请输入有效的步数", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int newGoal = Integer.parseInt(text);
                    if (newGoal <= 0) {
                        Toast.makeText(this,
                                "目标步数必须大于 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.setDailyGoal(newGoal);
                    String currentText = tvStepCount.getText().toString();
                    int currentSteps = currentText.isEmpty()
                            ? 0 : Integer.parseInt(currentText);
                    updateProgress(currentSteps);
                    Toast.makeText(this,
                            "目标已设为" + newGoal + " 步", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("省电优化设置")
                        .setMessage("为确保息屏后仍能正常计步并实时刷新通知，" +
                                "建议将本应用加入省电白名单，避免系统在后台限制计步服务。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("暂不设置", null)
                        .show();
            }
        }
    }
}
