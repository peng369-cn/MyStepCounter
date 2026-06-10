package com.pengchangwei.stepcounter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 排行榜页面，展示当天全平台步数排行。
 * Token过期时自动跳转登录页。
 */
public class RankingActivity extends AppCompatActivity {

    private RecyclerView recyclerRanking;
    private RankingAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        recyclerRanking = findViewById(R.id.recycler_ranking);
        tvEmpty = findViewById(R.id.tv_empty);
        recyclerRanking.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RankingAdapter();
        recyclerRanking.setAdapter(adapter);

        StepViewModel viewModel = new ViewModelProvider(this).get(StepViewModel.class);
        viewModel.loadRanking();
        viewModel.getRankingList().observe(this, items -> {
            if (items != null && !items.isEmpty()) {
                adapter.setItems(items);
                tvEmpty.setVisibility(View.GONE);
                recyclerRanking.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerRanking.setVisibility(View.GONE);
            }
        });

        // 认证失效兜底：Token刷新失败时提示用户并跳转登录页
        viewModel.getAuthFailed().observe(this, isAuthFailed -> {
            if (Boolean.TRUE.equals(isAuthFailed)) {
                Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                RetrofitClient.getInstance(this).clearTokens();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}
