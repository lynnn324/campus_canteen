package com.my.campus_canteen.model;

public class CampusPoi {

    public static final String TYPE_CANTEEN = "canteen";   // 食堂
    public static final String TYPE_BUILDING = "building"; // 教学楼

    private final String name;      // 真实名称
    private final double latitude;  // 真实纬度
    private final double longitude; // 真实经度
    private final String type;

    public CampusPoi(String name, double latitude, double longitude, String type) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
    }

    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getType() { return type; }
}
