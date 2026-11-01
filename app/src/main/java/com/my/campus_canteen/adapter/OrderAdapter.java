package com.my.campus_canteen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.model.Order;
import com.my.campus_canteen.model.OrderStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderHolder> {

    /** 卡片操作回调：取消 / 打开详情（由 OrderFragment 决定怎么做） */
    public interface OnOrderActionListener {
        void onCancel(Order order);
        void onOpen(Order order);
    }

    private final List<Order> orders = new ArrayList<>();
    private final OnOrderActionListener listener;

    public OrderAdapter(OnOrderActionListener listener) {
        this.listener = listener;
    }

    /** 整体替换订单列表 */
    public void replaceData(List<Order> newOrders) {
        orders.clear();
        orders.addAll(newOrders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new OrderHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderHolder holder, int position) {
        Order order = orders.get(position);
        Context ctx = holder.itemView.getContext();

        // 档口简述 · 共 X 份
        holder.tvSummary.setText(order.getShopsSummary() + " · 共" + order.getTotalCount() + "份");
        holder.tvTotal.setText("¥" + order.getTotal());

        // 下单时间：强制用北京时间（模拟器/设备默认时区可能是 UTC，不设会差 8 小时）
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String time = sdf.format(new Date(order.getOrderTime()));
        holder.tvTime.setText(time);

        // 状态条：文字 = 枚举的中文，底色 = 状态专属色
        OrderStatus status = order.getStatus();
        holder.tvStatus.setText(status.getLabel());
        holder.tvStatus.setBackgroundColor(ctx.getColor(statusColor(status)));

        // 卡片底色：同色系淡版
        holder.card.setBackgroundColor(ctx.getColor(statusBgColor(status)));

        // 只有"待出餐"才显示取消按钮（状态机规则）
        boolean cancellable = status == OrderStatus.PENDING;
        holder.btnCancel.setVisibility(cancellable ? View.VISIBLE : View.GONE);
        holder.btnCancel.setOnClickListener(v -> listener.onCancel(order));
        holder.btnOpen.setOnClickListener(v -> listener.onOpen(order));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    // ---------- 状态 → 颜色 映射（详情页时间轴也会复用） ----------

    /** 状态条专属色（亮色） */
    public static int statusColor(OrderStatus status) {
        switch (status) {
            case PENDING: return R.color.status_pending;
            case DELIVERING: return R.color.status_delivering;
            case DONE: return R.color.status_done;
            default: return R.color.status_cancel;
        }
    }

    /** 卡片浅色底色（淡版） */
    public static int statusBgColor(OrderStatus status) {
        switch (status) {
            case PENDING: return R.color.status_pending_bg;
            case DELIVERING: return R.color.status_delivering_bg;
            case DONE: return R.color.status_done_bg;
            default: return R.color.status_cancel_bg;
        }
    }

    // ViewHolder：缓存一张卡片的控件
    static class OrderHolder extends RecyclerView.ViewHolder {
        View card;
        TextView tvSummary;
        TextView tvTotal;
        TextView tvTime;
        TextView tvStatus;
        TextView btnCancel;
        TextView btnOpen;

        OrderHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.ll_order_card);
            tvSummary = itemView.findViewById(R.id.tv_order_summary);
            tvTotal = itemView.findViewById(R.id.tv_order_total);
            tvTime = itemView.findViewById(R.id.tv_order_time);
            tvStatus = itemView.findViewById(R.id.tv_order_status);
            btnCancel = itemView.findViewById(R.id.btn_order_cancel);
            btnOpen = itemView.findViewById(R.id.btn_order_open);
        }
    }
}
