package com.my.campus_canteen.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.OrderStore;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Order;
import com.my.campus_canteen.model.OrderStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class OrderDetailActivity extends AppCompatActivity {

    private long orderId; // 要展示的订单号（从 Intent 拿）

    // 时间轴控件
    private TextView tvCircle1, tvCircle2, tvCircle3;
    private TextView tvLabel1, tvLabel2, tvLabel3;
    private View viewLine1, viewLine2;

    // 明细容器
    private LinearLayout llItems;
    private TextView tvDetailTotal;
    private TextView tvDelivery;
    private TextView btnCancel;
    private TextView btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        // 标题栏返回箭头：点一下回到订单列表
        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        orderId = getIntent().getLongExtra("order_id", -1);

        tvCircle1 = findViewById(R.id.tv_circle_1);
        tvCircle2 = findViewById(R.id.tv_circle_2);
        tvCircle3 = findViewById(R.id.tv_circle_3);
        tvLabel1 = findViewById(R.id.tv_step_label_1);
        tvLabel2 = findViewById(R.id.tv_step_label_2);
        tvLabel3 = findViewById(R.id.tv_step_label_3);
        viewLine1 = findViewById(R.id.view_line_1);
        viewLine2 = findViewById(R.id.view_line_2);
        llItems = findViewById(R.id.ll_detail_items);
        tvDetailTotal = findViewById(R.id.tv_detail_total);
        tvDelivery = findViewById(R.id.tv_detail_delivery);
        btnCancel = findViewById(R.id.btn_cancel_order);
        btnNext = findViewById(R.id.btn_next_status);

        // 模拟状态流转：把当前订单往下一步推
        btnNext.setOnClickListener(v -> {
            Order order = OrderStore.findById(this, orderId);
            if (order == null) return;
            OrderStatus next = order.nextStep();
            if (next == null) {
                Toast.makeText(this, R.string.toast_no_next_step, Toast.LENGTH_SHORT).show();
                return;
            }
            OrderStore.updateStatus(this, orderId, next);
            render(); // 状态变了，重新画整个页面
        });

        // 取消订单（只有待出餐显示这个按钮）
        btnCancel.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(R.string.cancel_confirm_msg)
                .setPositiveButton(R.string.cancel_ok, (dialog, which) -> {
                    OrderStore.cancelOrder(this, orderId);
                    render();
                })
                .setNegativeButton(R.string.cancel_keep, null)
                .show());

        render();
    }

    /** 整页重画：读最新订单 → 填信息 → 画时间轴 → 控制按钮显隐 */
    private void render() {
        Order order = OrderStore.findById(this, orderId);
        if (order == null) {
            // 订单不存在（数据异常）：提示并退出
            Toast.makeText(this, R.string.toast_no_order, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 基本信息
        TextView tvId = findViewById(R.id.tv_order_id);
        TextView tvMode = findViewById(R.id.tv_order_mode);
        TextView tvTime = findViewById(R.id.tv_order_time);
        tvId.setText(getString(R.string.order_id_prefix, String.valueOf(order.getOrderId())));
        tvMode.setText(getString(R.string.order_mode_prefix,
                "takeout".equals(order.getMode())
                        ? getString(R.string.mode_takeout)
                        : getString(R.string.mode_dinein)));
        // 下单时间：强制用北京时间（模拟器/设备默认时区可能是 UTC，不设会差 8 小时）
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String time = sdf.format(new Date(order.getOrderTime()));
        tvTime.setText(getString(R.string.order_time_prefix, time));

        // 配送/取餐信息
        if ("takeout".equals(order.getMode())) {
            String line = getString(R.string.checkout_delivery_takeout,
                    order.getDeliveryFee(), order.getEstimatedMinutes());
            // 有送达位置就再补一行（老订单没有该字段，空串直接跳过）
            if (order.getDeliverySpot() != null && !order.getDeliverySpot().isEmpty()) {
                line = line + "\n" + getString(R.string.delivery_spot_prefix, order.getDeliverySpot());
            }
            tvDelivery.setText(line);
        } else {
            tvDelivery.setText(order.getPickTime() == null || order.getPickTime().isEmpty()
                    ? getString(R.string.detail_pick_no_book)
                    : getString(R.string.detail_pick_time, order.getPickTime()));
        }

        // 中间步骤文案：外卖="配送中"，自取="待取餐"
        tvLabel2.setText("takeout".equals(order.getMode())
                ? getString(R.string.step_label_mid)
                : getString(R.string.step_label_mid_dinein));

        // 菜品明细 + 合计
        llItems.removeAllViews();
        for (CartItem item : order.getItems()) {
            TextView row = new TextView(this);
            row.setText(item.getName() + " x" + item.getQuantity()
                    + "    ¥" + (item.getPrice() * item.getQuantity()));
            row.setTextSize(14f);
            row.setPadding(0, 6, 0, 6);
            llItems.addView(row);
        }
        tvDetailTotal.setText(getString(R.string.cart_total, order.getTotal()));

        // 时间轴：当前状态决定哪些节点"亮灯"
        OrderStatus status = order.getStatus();
        int currentStep = stepIndexOf(status); // 待出餐=0，配送中=1，已完成=2，已取消=-1
        drawTimeline(currentStep);

        // 取消按钮：只有待出餐能取消
        btnCancel.setVisibility(status == OrderStatus.PENDING ? View.VISIBLE : View.GONE);

        // 流转按钮：到终点（已完成/已取消）后禁用
        btnNext.setEnabled(order.nextStep() != null);
    }

    /** 状态 → 走到第几步（时间轴用） */
    private int stepIndexOf(OrderStatus status) {
        switch (status) {
            case PENDING: return 0;
            case DELIVERING: return 1;
            case DONE: return 2;
            default: return -1; // 已取消：整条时间轴都是灰的
        }
    }

    /** 画时间轴：走到的节点染成各自状态色，没走到的保持浅灰 */
    private void drawTimeline(int currentStep) {
        int colorGray = getColor(R.color.timeline_inactive);

        // 第 1 个圆点 + 文字：走到第 0 步（待出餐）就亮橙色
        setStep(tvCircle1, tvLabel1, currentStep >= 0, R.color.status_pending, R.color.timeline_inactive);
        // 连线 1：过了待出餐才亮
        viewLine1.setBackgroundColor(currentStep >= 1 ? getColor(R.color.status_delivering) : colorGray);
        // 第 2 个圆点：走到第 1 步（配送中/待取餐）亮青色
        setStep(tvCircle2, tvLabel2, currentStep >= 1, R.color.status_delivering, R.color.timeline_inactive);
        // 连线 2：过了配送中才亮
        viewLine2.setBackgroundColor(currentStep >= 2 ? getColor(R.color.status_done) : colorGray);
        // 第 3 个圆点：走到第 2 步（已完成）亮绿色
        setStep(tvCircle3, tvLabel3, currentStep >= 2, R.color.status_done, R.color.timeline_inactive);
    }

    /** 单个节点：亮了就染状态色+深色字，没亮就保持白灰+浅字 */
    private void setStep(TextView circle, TextView label, boolean active, int activeColorRes, int grayColorRes) {
        if (active) {
            // 亮起：圆底=状态色（语义色），圆内数字/文字=彩色底上的文字色，标签=主题主文字色
            circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(activeColorRes)));
            circle.setTextColor(MaterialColors.getColor(circle, com.google.android.material.R.attr.colorOnPrimary));
            label.setTextColor(MaterialColors.getColor(label, android.R.attr.textColorPrimary));
        } else {
            // 未亮：圆底=卡片面（主题面），数字/标签=次级灰
            circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(circle, com.google.android.material.R.attr.colorSurface)));
            circle.setTextColor(getColor(grayColorRes));
            label.setTextColor(MaterialColors.getColor(label, android.R.attr.textColorSecondary));
        }
    }
}
