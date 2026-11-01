package com.my.campus_canteen.ui.map;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.CampusPoiStore;
import com.my.campus_canteen.data.GeoUtil;
import com.my.campus_canteen.data.LocationHelper;

public class MapActivity extends AppCompatActivity {

    private static final float CAMPUS_ZOOM = 16f; // 校区放大级别：能看清建筑与食堂名
    // 未定位前的兜底中心：湖南科技大学（湘潭），避免镜头落到北京
    private static final LatLng DEFAULT_CENTER = new LatLng(27.9034, 112.9210);

    private MapView mapView;
    private AMap aMap;
    private LocationHelper locationHelper;
    private Marker myMarker;         // "我的位置"标记
    private BitmapDescriptor myIcon; // "我的位置"图标（画一次，重复使用）
    private boolean cameraMoved;     // 镜头只在第一次拿到定位时移动

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 高德 2021 年起要求先声明隐私合规，否则地图初始化会失败（必须在创建 MapView 之前）
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        findViewById(R.id.btn_map_back).setOnClickListener(v -> finish());

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState); // 必须转发，否则地图状态无法恢复

        aMap = mapView.getMap();
        aMap.getUiSettings().setZoomControlsEnabled(false); // 用双指缩放，去掉挡视野的按钮

        // 有上次搜索到的校区中心就先定过去，否则用校区兜底中心；等定位到了再移过去
        double[] anchor = CampusPoiStore.anchor(this);
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                anchor != null ? new LatLng(anchor[0], anchor[1]) : DEFAULT_CENTER,
                anchor != null ? CAMPUS_ZOOM : 15f));

        // 复用项目定位：拿到位置就画"我的位置"
        locationHelper = new LocationHelper(this, new LocationHelper.OnLocationResultListener() {
            @Override
            public void onLocation(Location location) {
                // 系统定位可能是 WGS-84，也可能（国产手机的网络定位）已经是 GCJ-02，统一判断后再画
                double[] mapPoint = GeoUtil.toGcj(MapActivity.this, location);
                Location here = new Location(location);
                here.setLatitude(mapPoint[0]);
                here.setLongitude(mapPoint[1]);
                showMyLocation(here);
            }

            @Override
            public void onLocationUnavailable() {
                // 没定位也不影响看地图，静默即可
            }
        });
        if (LocationHelper.hasPermission(this)) {
            locationHelper.start();
        }
    }

    /** 只画"我的位置"：底图已有食堂/教学楼原生标注，不再叠加自绘标记 */
    private void showMyLocation(Location location) {
        LatLng here = new LatLng(location.getLatitude(), location.getLongitude());
        if (myMarker == null) {
            if (myIcon == null) myIcon = buildMyLocationIcon();
            myMarker = aMap.addMarker(new MarkerOptions()
                    .position(here)
                    .title(getString(R.string.map_my_location))
                    .icon(myIcon)
                    .anchor(0.5f, 0.5f)); // 圆心对准坐标点，而不是图钉尖
        } else {
            myMarker.setPosition(here);
        }
        if (!cameraMoved) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(here, CAMPUS_ZOOM));
            cameraMoved = true;
        }
    }

    private BitmapDescriptor buildMyLocationIcon() {
        int size = Math.round(56 * getResources().getDisplayMetrics().density); // 56dp 直径
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;
        paint.setColor(0x332A6BE0);                  // 外圈淡蓝色光晕
        canvas.drawCircle(center, center, center, paint);
        paint.setColor(Color.WHITE);                 // 白色描边，压住底图杂色
        canvas.drawCircle(center, center, center * 0.70f, paint);
        paint.setColor(0xFF2A6BE0);                  // 蓝色实心点
        canvas.drawCircle(center, center, center * 0.56f, paint);
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    // ---------- MapView 生命周期必须逐个转发，否则会黑屏/泄漏 ----------

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (locationHelper != null) locationHelper.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationHelper != null) locationHelper.stop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (locationHelper != null) locationHelper.destroy();
        mapView.onDestroy();
        super.onDestroy();
    }
}
