package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;

public class DeliverySpots {

    private static final String PREF = "delivery_spot_pref";
    private static final String KEY_NAME = "spot_name";
    private static final String KEY_LAT = "spot_lat";
    private static final String KEY_LNG = "spot_lng";

    /** 一个送达位置 = 名称 + 经纬度（经纬度是高德 GCJ-02 坐标） */
    public static class Spot {
        private final String name;
        private final double latitude;
        private final double longitude;

        public Spot(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getName() { return name; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }

    private DeliverySpots() {
        // 工具类：私有构造，只允许静态调用
    }

    /** 读取上次选择的送达位置；没选过返回 null（由页面提示用户去搜索） */
    public static Spot getSelected(Context c) {
        SharedPreferences sp = sp(c);
        String name = sp.getString(KEY_NAME, "");
        if (name.isEmpty()) return null;
        double lat = parseDouble(sp.getString(KEY_LAT, "0"));
        double lng = parseDouble(sp.getString(KEY_LNG, "0"));
        return new Spot(name, lat, lng);
    }

    /** 记住本次选择的送达位置 */
    public static void setSelected(Context c, Spot spot) {
        if (spot == null) return;
        sp(c).edit()
                .putString(KEY_NAME, spot.getName())
                .putString(KEY_LAT, String.valueOf(spot.getLatitude()))
                .putString(KEY_LNG, String.valueOf(spot.getLongitude()))
                .apply();
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
