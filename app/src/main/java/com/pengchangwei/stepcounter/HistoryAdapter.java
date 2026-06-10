package com.pengchangwei.stepcounter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * 历史步数列表的 RecyclerView 适配器。
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<StepRecord> records;

    public HistoryAdapter(List<StepRecord> records) {
        this.records = records;
    }

    public void setRecords(List<StepRecord> records) {
        this.records = records;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_step_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StepRecord record = records.get(position);
        holder.tvDate.setText(record.getDate());
        holder.tvSteps.setText(String.valueOf(record.getSteps()));
        double km = record.getSteps() * 0.7 / 1000.0;
        holder.tvDistance.setText(String.format(Locale.getDefault(), " 步 / %.2fkm", km));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        TextView tvSteps;
        TextView tvDistance;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvSteps = itemView.findViewById(R.id.tv_steps);
            tvDistance = itemView.findViewById(R.id.tv_distance);
        }
    }
}
