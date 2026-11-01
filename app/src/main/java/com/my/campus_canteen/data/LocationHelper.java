package com.my.campus_canteen.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;

public class LocationHelper {

    /** 定位回调：拿到位置 / 暂时拿不到位置 */
    public interface OnLocationResultListener {
        void onLocation(Location location);
        void onLocationUnavailable();
    }

    // 系统定位刷新节奏：最短 2 秒一次、移动超过 1 米才回调
    private static final long MIN_TIME_MS = 2000L;
    private static final float MIN_DISTANCE_M = 1f;
    // 高德定位的刷新间隔（毫秒）
    private static final long AMAP_INTERVAL_MS = 2000L;
    // 缓存位置超过 10 分钟就不可信（可能是上次在别处留下的），宁可先不显示距离
    private static final long MAX_CACHE_AGE_MS = 10 * 60 * 1000L;
    // 首次定位：精度优于 80 米才显示，避免先用基站粗定位把距离算偏
    private static final float FIRST_FIX_ACCURACY_M = 80f;
    // 8 秒内一直等不到高精度定位，就先用现有结果兜底，别让界面一直空着
    private static final long FIRST_FIX_TIMEOUT_MS = 8000L;
    // 移动不足 50 米视为定位抖动，不刷新距离数字
    private static final float MIN_MOVE_M = 50f;
    // 精度明显变好（超过 20 米）时允许刷新一次
    private static final float ACCURACY_GAIN_M = 20f;
    // 系统网络定位与刚拿到的高德位置相差超过 300 米，视为不可信（几秒内走不了这么远）
    private static final float MAX_SYSTEM_JUMP_M = 300f;
    // 高德位置在多长时间内仍算"刚拿到"
    private static final long RECENT_AMAP_FIX_MS = 60_000L;
    // 高德定位失败后，隔多久重试一次
    private static final long AMAP_RETRY_DELAY_MS = 1200L;

    private final Context context;
    private final OnLocationResultListener callback;
    private final LocationManager locationManager;
    private final LocationListener systemListener;

    private AMapLocationClient amapClient; // 高德定位客户端（可能为空）
    private boolean systemStarted = false; // 系统定位是否已订阅
    private boolean started = false;

    private Location lastDelivered;        // 上次真正交给界面的位置（用于过滤抖动）
    private float bestAccuracy = Float.MAX_VALUE;
    private long firstStartMs;
    private Location lastAmapFix;         // 最近一次高德位置（系统定位要跟它比对）
    private long lastAmapFixMs;
    private boolean amapRetried;          // 本次会话是否已重试过高德定位
    private final android.os.Handler retryHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public LocationHelper(Context context, OnLocationResultListener callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.locationManager =
                (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        this.systemListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                onSystemFix(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // 状态变化无需处理
            }

            @Override
            public void onProviderEnabled(String provider) {
                // 定位开关打开无需处理
            }

            @Override
            public void onProviderDisabled(String provider) {
                // 定位开关关闭无需处理
            }
        };
    }

    /** 是否已拿到定位权限（精确或粗略任一即可） */
    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 开始定位：优先高德定位；初始化失败时自动退回系统定位 */
    public void start() {
        if (!hasPermission(context)) {
            callback.onLocationUnavailable();
            return;
        }
        if (started) return;
        started = true;
        if (lastDelivered == null) {
            firstStartMs = System.currentTimeMillis();
        }
        amapRetried = false;
        if (!startAmap()) {
            startSystem();
        }
    }

    /** 启动高德定位；返回 false 表示不可用（由调用方退回系统定位） */
    private boolean startAmap() {
        try {
            // 高德 2021 年起要求先声明隐私合规，否则定位初始化会失败
            AMapLocationClient.updatePrivacyShow(context, true, true);
            AMapLocationClient.updatePrivacyAgree(context, true);

            amapClient = new AMapLocationClient(context);
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(AMAP_INTERVAL_MS);
            option.setNeedAddress(false);      // 只要经纬度，不反查门牌地址
            option.setOnceLocation(false);     // 持续定位，走路时距离会跟着变
            option.setLocationCacheEnable(true);
            option.setMockEnable(false);       // 拒绝模拟位置
            amapClient.setLocationOption(option);

            amapClient.setLocationListener(location -> {
                if (location == null) return;
                if (location.getErrorCode() != 0) {
                    android.util.Log.w("LocationHelper", "高德定位失败 code=" + location.getErrorCode()
                            + " info=" + location.getErrorInfo());
                    // 高德定位失败（如机型/网络限制）：退回系统定位，别让界面一直空着
                    fallbackToSystem();
                    return;
                }
                if (isFresh(location)) {
                    deliver(location);
                }
            });

            // 先给一个上次已知位置，界面立刻有数
            AMapLocation last = amapClient.getLastKnownLocation();
            if (last != null && last.getErrorCode() == 0 && isFresh(last)) {
                deliver(last);
            }

            amapClient.startLocation();
            return true;
        } catch (Throwable t) {
            android.util.Log.w("LocationHelper", "amap unavailable", t);
            // 定位引擎不可用（极端情况）时走系统兜底，不让应用崩溃
            releaseAmap();
            return false;
        }
    }

    /**
     * 高德定位失败时：刚拿到的位置还新，就先重试一次高德，
     * 避免直接用偏差很大的系统网络定位把正确距离改坏；重试仍失败才退回系统定位。
     */
    private void fallbackToSystem() {
        releaseAmap();
        boolean recent = lastAmapFix != null
                && System.currentTimeMillis() - lastAmapFixMs < RECENT_AMAP_FIX_MS;
        if (recent && !amapRetried) {
            amapRetried = true;
            retryHandler.postDelayed(this::retryAmap, AMAP_RETRY_DELAY_MS);
            return;
        }
        if (!systemStarted) startSystem();
    }

    /** 重试高德定位；还是失败就退回系统定位 */
    private void retryAmap() {
        if (!started) return;
        if (!startAmap() && !systemStarted) {
            startSystem();
        }
    }

    private void startSystem() {
        if (locationManager == null) {
            callback.onLocationUnavailable();
            return;
        }
        try {
            // ① 先用系统缓存的最后位置，避免界面空等
            Location last = bestLastKnownLocation();
            if (last != null) {
                onSystemFix(last);
            } else {
                callback.onLocationUnavailable();
            }

            // ② 再订阅 GPS / 网络定位的实时更新（哪个可用订哪个）
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_MS, MIN_DISTANCE_M, systemListener, Looper.getMainLooper());
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_MS, MIN_DISTANCE_M, systemListener, Looper.getMainLooper());
                requested = true;
            }
            if (!requested) {
                callback.onLocationUnavailable();
            }
            systemStarted = requested;
        } catch (SecurityException e) {
            // 权限被临时收回等极端情况：走兜底，不让应用崩溃
            callback.onLocationUnavailable();
        }
    }

    /** 停止定位（页面不可见时调用），但保留客户端，回来时可直接再 start */
    public void stop() {
        if (amapClient != null) {
            try {
                amapClient.stopLocation();
            } catch (Throwable ignored) {
                // 忽略停止异常
            }
        }
        stopSystem();
        started = false;
    }

    /** 彻底销毁定位资源：页面真正销毁时调用，避免监听器与定位客户端泄漏 */
    public void destroy() {
        stop();
        releaseAmap();
        retryHandler.removeCallbacksAndMessages(null);
    }

    private void stopSystem() {
        if (locationManager == null || !systemStarted) return;
        try {
            locationManager.removeUpdates(systemListener);
        } catch (SecurityException ignored) {
            // 权限被收回时忽略
        }
        systemStarted = false;
    }

    private void releaseAmap() {
        if (amapClient == null) return;
        try {
            amapClient.stopLocation();
            amapClient.onDestroy();
        } catch (Throwable ignored) {
            // 忽略销毁异常
        }
        amapClient = null;
    }

    /**
     * 把高德定位结果交给界面：额外打上"gcj02"标记，
     * 让 GeoUtil 知道它已经是高德坐标系，不要再转一次（转一次会偏几百米）。
     */
    private void deliver(Location location) {
        if (!shouldDeliver(location)) return;
        android.util.Log.d("LocationHelper", "amap fix lat=" + location.getLatitude()
                + " lng=" + location.getLongitude()
                + " acc=" + accuracyOf(location));
        markDelivered(location);
        lastAmapFix = new Location(location);
        lastAmapFixMs = System.currentTimeMillis();
        Bundle extras = location.getExtras();
        Bundle tagged = extras == null ? new Bundle() : new Bundle(extras);
        tagged.putBoolean("gcj02", true);
        location.setExtras(tagged);
        callback.onLocation(location);
    }

    /** 系统定位结果：同样经过抖动过滤再交给界面 */
    private void onSystemFix(Location location) {
        if (location == null) return;
        boolean recent = lastAmapFix != null
                && System.currentTimeMillis() - lastAmapFixMs < RECENT_AMAP_FIX_MS;
        if (recent && lastAmapFix.distanceTo(location) > MAX_SYSTEM_JUMP_M) {
            return;
        }
        if (!shouldDeliver(location)) return;
        android.util.Log.d("LocationHelper", "system fix provider=" + location.getProvider()
                + " lat=" + location.getLatitude() + " lng=" + location.getLongitude()
                + " acc=" + accuracyOf(location));
        markDelivered(location);
        callback.onLocation(location);
    }

    /** 精度（米）：拿不到时按很粗处理 */
    private float accuracyOf(Location location) {
        return location.hasAccuracy() ? location.getAccuracy() : 1000f;
    }

    /**
     * 是否值得刷新界面：
     * 首次定位等精度进入 80 米以内（最多等 8 秒）；
     * 之后位移超过 50 米才算真的走动，否则只在精度明显变好时刷新。
     */
    private boolean shouldDeliver(Location location) {
        float accuracy = accuracyOf(location);
        if (lastDelivered == null) {
            boolean timeout = System.currentTimeMillis() - firstStartMs >= FIRST_FIX_TIMEOUT_MS;
            return timeout || accuracy <= FIRST_FIX_ACCURACY_M;
        }
        if (lastDelivered.distanceTo(location) >= MIN_MOVE_M) return true;
        return accuracy <= bestAccuracy - ACCURACY_GAIN_M;
    }

    private void markDelivered(Location location) {
        lastDelivered = new Location(location);
        bestAccuracy = Math.min(bestAccuracy, accuracyOf(location));
    }

    /** 位置是否够新（太旧的缓存可能是几天前在别处留下的） */
    private boolean isFresh(Location location) {
        long time = location.getTime();
        return time > 0 && System.currentTimeMillis() - time <= MAX_CACHE_AGE_MS;
    }

    /** 从 GPS / 网络 / 被动定位中挑一个最新的缓存位置（太旧的不算） */
    private Location bestLastKnownLocation() {
        Location best = null;
        long now = System.currentTimeMillis();
        String[] providers = {
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        for (String provider : providers) {
            try {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate == null) continue;
                if (now - candidate.getTime() > MAX_CACHE_AGE_MS) continue; // 缓存太旧，丢掉
                if (best == null || candidate.getTime() > best.getTime()) {
                    best = candidate;
                }
            } catch (SecurityException ignored) {
                // 没有权限时跳过该 provider
            }
        }
        return best;
    }
}
