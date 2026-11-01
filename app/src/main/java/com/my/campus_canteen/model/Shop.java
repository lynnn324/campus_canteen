package com.my.campus_canteen.model;

import java.util.List;

public class Shop {

    private int shopId;                     // 档口编号（唯一）
    private String name;
    private List<String> tags;              // 标签，如"早餐/快取"
    private String intro;
    private boolean supportTakeout;         // 是否支持外卖（双模切换过滤用）
    private int deliveryFee;                // 配送费（元）
    private int minPrice;                   // 起送价（元），列表卡片上展示
    private int monthlySales;               // 月售份数，列表卡片上展示
    private int estimatedTime;              // 预计送达/出餐时间（分钟）
    private double latitude;                // 食堂纬度（算"距你多远"用）
    private double longitude;               // 食堂经度（算"距你多远"用）

    public Shop(int shopId, String name, List<String> tags, String intro,
                boolean supportTakeout, int deliveryFee, int minPrice, int monthlySales,
                int estimatedTime, double latitude, double longitude) {
        this.shopId = shopId;
        this.name = name;
        this.tags = tags;
        this.intro = intro;
        this.supportTakeout = supportTakeout;
        this.deliveryFee = deliveryFee;
        this.minPrice = minPrice;
        this.monthlySales = monthlySales;
        this.estimatedTime = estimatedTime;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // getter：读数据用（Java 惯例：字段私有，用方法读）
    public int getShopId() { return shopId; }
    public String getName() { return name; }
    public List<String> getTags() { return tags; }
    public String getIntro() { return intro; }
    public boolean isSupportTakeout() { return supportTakeout; }
    public int getDeliveryFee() { return deliveryFee; }
    public int getMinPrice() { return minPrice; }
    public int getMonthlySales() { return monthlySales; }
    public int getEstimatedTime() { return estimatedTime; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public void setGeo(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
