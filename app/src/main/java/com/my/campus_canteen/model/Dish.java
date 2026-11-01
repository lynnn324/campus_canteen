package com.my.campus_canteen.model;

public class Dish {

    private int dishId;                 // 菜品编号（唯一）
    private int shopId;                 // 属于哪个档口
    private String name;
    private String type;                // 种类：主食 / 菜 / 轻食 / 饮品（反浪费统计按它判断）
    private String category;            // 归属专区：早餐 / 套餐 / 轻食 / 饮品（首页四分类入口用它筛选）
    private double price;
    private boolean smallPortionWarn;   // 是否"分量偏少"提示
    private boolean canTakeout;
    private boolean canDineIn;
    private String label;               // 趣味标签（如"招牌必点/轻食人气王"），可为空
    private String desc;                // 一句话描述（口味、用料、适合谁），详情页展示，可为空
    private String image;               // 配图：drawable 资源名（如 "dish_steamed_bun"），可为空

    public Dish(int dishId, int shopId, String name, String type, String category, double price,
                boolean smallPortionWarn, boolean canTakeout, boolean canDineIn, String label,
                String desc, String image) {
        this.dishId = dishId;
        this.shopId = shopId;
        this.name = name;
        this.type = type;
        this.category = category;
        this.price = price;
        this.smallPortionWarn = smallPortionWarn;
        this.canTakeout = canTakeout;
        this.canDineIn = canDineIn;
        this.label = label;
        this.desc = desc;
        this.image = image;
    }

    public int getDishId() { return dishId; }
    public int getShopId() { return shopId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public boolean isSmallPortionWarn() { return smallPortionWarn; }
    public boolean isCanTakeout() { return canTakeout; }
    public boolean isCanDineIn() { return canDineIn; }
    public String getLabel() { return label; }
    public String getDesc() { return desc; }
    public String getImage() { return image; }
}
