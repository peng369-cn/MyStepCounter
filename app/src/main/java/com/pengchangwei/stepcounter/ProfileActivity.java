package com.pengchangwei.stepcounter;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 个人中心页面，展示用户信息、累计步数/里程，支持退出登录。
 * profile接口失败时页面不关闭，退出登录按钮始终可用，避免用户被困。
 */
public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvNickname = findViewById(R.id.tv_nickname);
        TextView tvUsername = findViewById(R.id.tv_username);
        TextView tvTotalSteps = findViewById(R.id.tv_total_steps);
        TextView tvTotalDistance = findViewById(R.id.tv_total_distance);
        MaterialButton btnLogout = findViewById(R.id.btn_logout);

        btnLogout.setOnClickListener(v -> showLogoutConfirm());
        loadProfile(tvNickname, tvUsername, tvTotalSteps, tvTotalDistance);
    }

    /**
     * 调后端获取用户profile数据。
     * 401/500：直接清Token跳登录页。
     * 其他失败：Toast提示，但不关闭页面，保留退出登录按钮让用户自救。
     */
    private void loadProfile(TextView tvNickname, TextView tvUsername,
                             TextView tvTotalSteps, TextView tvTotalDistance) {
        RetrofitClient.getInstance(this).getApiService()
                .getProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                           @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.code() == 401) {
                            Toast.makeText(ProfileActivity.this,
                                    "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                            logout();
                            return;
                        }
                        if (response.code() == 500) {
                            Toast.makeText(ProfileActivity.this,
                                    "服务异常，请重新登录", Toast.LENGTH_SHORT).show();
                            logout();
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            Map<String, Object> data = response.body().getData();
                            if (data != null) {
                                tvNickname.setText(String.valueOf(data.get("nickname")));
                                tvUsername.setText(String.format("@%s", data.get("username")));
                                tvTotalSteps.setText(formatNumber(data.get("totalSteps")));
                                tvTotalDistance.setText(formatDistance(data.get("totalDistance")));
                            }
                        } else {
                            String msg = "加载失败";
                            if (response.body() != null && response.body().getMessage() != null) {
                                msg = response.body().getMessage();
                            }
                            Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                          @NonNull Throwable t) {
                        Toast.makeText(ProfileActivity.this,
                                "网络错误：" + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** 退出登录确认弹窗 */
    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> logout())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 清空本地Token、停止计步服务、跳转登录页 */
    private void logout() {
        RetrofitClient.getInstance(this).clearTokens();
        stopService(new Intent(this, StepCounterService.class));
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String formatNumber(Object value) {
        if (value instanceof Number) {
            return String.valueOf(((Number) value).longValue());
        }
        return "0";
    }

    private String formatDistance(Object value) {
        if (value instanceof Number) {
            return String.format(Locale.getDefault(), "%.2f km", ((Number) value).doubleValue());
        }
        return "0.00 km";
    }
}
