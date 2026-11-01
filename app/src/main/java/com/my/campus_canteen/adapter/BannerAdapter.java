package com.my.campus_canteen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerHolder> {

    /** 一页轮播的内容：背景色 + 推荐文案（首页时空适配时组装） */
    public static class BannerItem {
        public final int color;   // 背景色（占位色板，后续可换本地图）
        public final String text; // 推荐文案，如"早餐时光 · 晨光早餐铺：鲜肉包"

        public BannerItem(int color, String text) {
            this.color = color;
            this.text = text;
        }
    }

    private final List<BannerItem> items;

    public BannerAdapter(List<BannerItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public BannerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 为每一页创建 View
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_banner, parent, false);
        return new BannerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerHolder holder, int position) {
        // 给当前这一页填内容：背景色 + 文案
        BannerItem item = items.get(position);
        holder.tvBanner.setBackgroundColor(item.color);
        holder.tvBanner.setText(item.text);
    }

    @Override
    public int getItemCount() {
        // 返回共有几页（轮播图张数）
        return items.size();
    }

    // ViewHolder：把一页里的控件引用缓存起来，避免每次找，省性能
    static class BannerHolder extends RecyclerView.ViewHolder {
        TextView tvBanner;

        BannerHolder(@NonNull View itemView) {
            super(itemView);
            tvBanner = itemView.findViewById(R.id.tv_banner);
        }
    }
}
