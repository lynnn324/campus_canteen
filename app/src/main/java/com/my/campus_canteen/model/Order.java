package com.my.campus_canteen.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Order {

    private long orderId;           // 订单号（用下单时间戳生成，唯一）
    private List<CartItem> items;   // 菜品快照列表
    private double total;           // 应付总额（已含配送费）
    private String mode;            // 点餐模式：dinein 自取 / takeout 外卖
    private OrderStatus status;     // 当前状态（枚举）
    private int deliveryFee;        // 配送费（仅外卖有，自取为 0）
    private int estimatedMinutes;   // 预计出餐分钟数（自取倒计时用）
    private long orderTime;         // 下单时间戳（列表倒序排序用）
    private String pickTime;        // 自取预约取餐时间（如 "12:30"，可不选）
    private String deliverySpot;    // 外卖送达位置（如 "图书馆"，自取为空串）

    public Order(long orderId, List<CartItem> items, double total, String mode,
                 OrderStatus status, int deliveryFee, int estimatedMinutes,
                 long orderTime, String pickTime, String deliverySpot) {
        this.orderId = orderId;
        this.items = items;
        this.total = total;
        this.mode = mode;
        this.status = status;
        this.deliveryFee = deliveryFee;
        this.estimatedMinutes = estimatedMinutes;
        this.orderTime = orderTime;
        this.pickTime = pickTime;
        this.deliverySpot = deliverySpot;
    }

    // ---- getter / setter ----
    public long getOrderId() { return orderId; }
    public List<CartItem> getItems() { return items; }
    public double getTotal() { return total; }
    public String getMode() { return mode; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public int getDeliveryFee() { return deliveryFee; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public long getOrderTime() { return orderTime; }
    public String getPickTime() { return pickTime; }
    public String getDeliverySpot() { return deliverySpot; }

    /** 菜品总份数（倒计时估算出餐时间用） */
    public int getTotalCount() {
        int count = 0;
        for (CartItem item : items) {
            count += item.getQuantity();
        }
        return count;
    }

    /** 涉及几个档口的简述（如 "晨光早餐铺、轻食星球"） */
    public String getShopsSummary() {
        Set<String> names = new LinkedHashSet<>();
        for (CartItem item : items) {
            // 档口名缺失的行跳过，避免最终拼出 "null"
            if (item.getShopName() != null) {
                names.add(item.getShopName());
            }
        }
        // 用 StringBuilder 手动拼接（不用 String.join：那是 API 26+，本项目 minSdk 24）
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /**
     * 状态机"下一步"：
     * 自取：待出餐 → 已完成（没有配送环节）
     * 外卖：待出餐 → 配送中 → 已完成
     * 已完成/已取消 没有下一步。
     */
    public OrderStatus nextStep() {
        if (status == OrderStatus.PENDING) {
            return "takeout".equals(mode) ? OrderStatus.DELIVERING : OrderStatus.DONE;
        }
        if (status == OrderStatus.DELIVERING) {
            return OrderStatus.DONE;
        }
        return null; // 已到终点，不能再往前走
    }
}
