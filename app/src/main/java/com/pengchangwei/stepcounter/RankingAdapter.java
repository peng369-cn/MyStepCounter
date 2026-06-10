package com.pengchangwei.stepcounter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 排行榜列表适配器。
 */
public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ViewHolder> {

    private List<RankingItem> items;

    public void setItems(List<RankingItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RankingItem item = items.get(position);
        holder.tvRank.setText(String.valueOf(item.getRank()));
        holder.tvNickname.setText(item.getNickname());
        holder.tvSteps.setText(String.valueOf(item.getStepCount()));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank;
        TextView tvNickname;
        TextView tvSteps;

        ViewHolder(View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvSteps = itemView.findViewById(R.id.tv_step_count);
        }
    }
}
