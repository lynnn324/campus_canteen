package com.my.campus_canteen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.model.Shop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StallAdapter extends RecyclerView.Adapter<StallAdapter.StallHolder> {

    // 回调接口：点击一行时，把被点的档口交出去（由 HomeFragment 负责跳转）
    public interface OnShopClickListener {
        void onShopClick(Shop shop);
    }

    private final List<Shop> shops;
    private final OnShopClickListener listener;
    // 档口编号 → 距离文案（如"距你 230 m"）；定位更新时由上层整体替换
    private final Map<Integer, String> distanceTexts = new HashMap<>();

    public StallAdapter(List<Shop> shops, OnShopClickListener listener) {
        this.shops = shops;
        this.listener = listener;
    }

    /** 整体替换列表内容（切换外卖/自取模式时用来刷新） */
    public void replaceData(List<Shop> newShops) {
        this.shops.clear();
        this.shops.addAll(newShops);
        notifyDataSetChanged();
    }

    /** 更新各档口的实时距离文案（传空 Map 表示暂时没有定位） */
    public void setDistances(Map<Integer, String> newDistances) {
        distanceTexts.clear();
        if (newDistances != null) {
            distanceTexts.putAll(newDistances);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StallHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 为每一行创建一个 View（用 item_stall.xml 这个"卡片模板"）
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stall, parent, false);
        return new StallHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StallHolder holder, int position) {
        // 给这一行填内容
        Shop shop = shops.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvName.setText(shop.getName());

        // 特色标签：把 tags 用" · "拼成一行，没有标签就整块隐藏
        String tagsText = joinTags(shop.getTags());
        if (tagsText.isEmpty()) {
            holder.tvTags.setVisibility(View.GONE);
        } else {
            holder.tvTags.setVisibility(View.VISIBLE);
            holder.tvTags.setText(tagsText);
        }

        // 月售 · 起送价 · 配送费；免配送费时用专门文案，避免出现"配送 ¥0"
        if (shop.getDeliveryFee() == 0) {
            holder.tvMeta.setText(ctx.getString(R.string.stall_meta_free,
                    shop.getMonthlySales(), shop.getMinPrice()));
        } else {
            holder.tvMeta.setText(ctx.getString(R.string.stall_meta,
                    shop.getMonthlySales(), shop.getMinPrice(), shop.getDeliveryFee()));
        }

        holder.tvIntro.setText(shop.getIntro());
        holder.tvEta.setText(ctx.getString(R.string.stall_eta, shop.getEstimatedTime()));

        // 实时距离：有定位数据才显示，没有就整块隐藏，避免出现"距你 0 m"的假数据
        String distance = distanceTexts.get(shop.getShopId());
        if (distance == null || distance.isEmpty()) {
            holder.tvDistance.setVisibility(View.GONE);
        } else {
            holder.tvDistance.setVisibility(View.VISIBLE);
            holder.tvDistance.setText(distance);
        }

        // 整行可点击：点了就把这个档口交给上层 listener
        holder.itemView.setOnClickListener(v -> listener.onShopClick(shop));
    }

    /** 把标签列表拼成"早餐 · 热粥 · 人气"；为空时返回空串 */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return shops.size();
    }

    // ViewHolder：缓存一行内的控件引用，避免每次都去布局里找，快
    static class StallHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvTags;
        TextView tvMeta;
        TextView tvIntro;
        TextView tvDistance;
        TextView tvEta;

        StallHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_stall_name);
            tvTags = itemView.findViewById(R.id.tv_stall_tags);
            tvMeta = itemView.findViewById(R.id.tv_stall_meta);
            tvIntro = itemView.findViewById(R.id.tv_stall_intro);
            tvDistance = itemView.findViewById(R.id.tv_stall_distance);
            tvEta = itemView.findViewById(R.id.tv_stall_eta);
        }
    }
}
