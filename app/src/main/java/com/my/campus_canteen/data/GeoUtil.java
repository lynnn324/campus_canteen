package com.my.campus_canteen.data;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.amap.api.maps.CoordinateConverter;
import com.amap.api.maps.model.LatLng;

public class GeoUtil {

    private GeoUtil() {
        // 工具类：只允许静态调用
    }

    /**
     * 把系统定位回调的 Location 换成高德用的 GCJ-02。
     * 注意：国内不少手机（如小米）的网络/融合定位本身由高德提供，返回的坐标已经是 GCJ-02
     * （Location 的 extras 里带 amap 标记）；这类位置若再转一次，会整体偏出几百米。
     */
    public static double[] toGcj(Context context, Location location) {
        if (isAlreadyGcj(location)) {
            return new double[]{location.getLatitude(), location.getLongitude()};
        }
        return wgsToGcj(context, location.getLatitude(), location.getLongitude());
    }

    /** 判断这个定位结果是否已经是国内加密坐标（高德系定位会在 extras 里留标记） */
    private static boolean isAlreadyGcj(Location location) {
        Bundle extras = location.getExtras();
        if (extras == null) return false;
        return extras.containsKey("amap")
                || extras.containsKey("amap_location_result_info")
                || extras.containsKey("gcj02");
    }

    /**
     * 系统定位坐标（WGS-84）→ 高德坐标（GCJ-02）。
     * 转换失败时原样返回，保证功能不中断。
     */
    public static double[] wgsToGcj(Context context, double lat, double lng) {
        try {
            CoordinateConverter converter = new CoordinateConverter(context);
            converter.from(CoordinateConverter.CoordType.GPS);
            converter.coord(new LatLng(lat, lng));
            LatLng gcj = converter.convert();
            if (gcj != null) {
                return new double[]{gcj.latitude, gcj.longitude};
            }
        } catch (Exception ignored) {
            // 转换异常时退回原坐标
        }
        return new double[]{lat, lng};
    }
}
