package com.my.campus_canteen.model;

public class CartItem {

    private int dishId;          // 菜品编号（唯一，用来做"同菜+1"）
    private int shopId;          // 所属档口 id（按档口分组用）
    private String shopName;     // 所属档口名（分组标题显示用）
    private String name;
    private String type;         // 分类：主食/菜/轻食/饮品
    private double price;
    private int quantity;
    private boolean canTakeout;

    public CartItem(int dishId, int shopId, String shopName, String name, String type,
                    double price, int quantity, boolean canTakeout) {
        this.dishId = dishId;
        this.shopId = shopId;
        this.shopName = shopName;
        this.name = name;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.canTakeout = canTakeout;
    }

    // getter / setter：读写字段的标准入口
    public int getDishId() { return dishId; }
    public int getShopId() { return shopId; }
    public String getShopName() { return shopName; }
    public String getName() { return name; }
    public String getType() { return type; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isCanTakeout() { return canTakeout; }
}
