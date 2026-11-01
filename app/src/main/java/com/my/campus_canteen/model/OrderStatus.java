package com.my.campus_canteen.model;

public enum OrderStatus {

    PENDING("待出餐"),      // 刚下单，后厨正在做（可取消）
    DELIVERING("配送中"),   // 外卖：骑手在路上（仅外卖模式出现）
    DONE("已完成"),         // 取到餐/签收，订单结束
    CANCELED("已取消");     // 用户取消（只能在待出餐时取消）

    private final String label; // 界面显示的中文

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
