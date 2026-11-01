package com.my.campus_canteen.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.BannerAdapter;
import com.my.campus_canteen.adapter.StallAdapter;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.data.CampusPoiStore;
import com.my.campus_canteen.data.DataSync;
import com.my.campus_canteen.data.GeoUtil;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.data.LocationHelper;
import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.model.Shop;
import com.my.campus_canteen.ui.category.CategoryActivity;
import com.my.campus_canteen.ui.map.MapActivity;
import com.my.campus_canteen.ui.shop.StallDetailActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class HomeFragment extends Fragment implements DataSync.Listener {

    private RecyclerView rvStallList;
    private TextView tvEmpty;
    private TextView tvStallTitle;
    private EditText etSearch;
    private StallAdapter stallAdapter;
    private List<Shop> allShops;            // 全部档口（搜索/过滤的数据源）
    private List<Dish> allDishes;           // 全部菜品（搜索菜名要用）
    private boolean takeoutMode = false;    // 当前是否"外卖"模式（默认自取）
    private RadioGroup rgMode;

    // ---------- 定位相关 ----------
    private static final int REQ_LOCATION = 1001; // 定位权限申请码
    private static final String PREF_LOCATION = "location_pref";
    private static final String KEY_LOCATION_ASKED = "asked";
    private static final float MAX_SHOW_DISTANCE_M = 50000f; // 超过 50 公里就不显示距离
    private LocationHelper locationHelper;
    // 档口编号 → 距我多少米（实时距离，既用来排序，也用来显示）
    private final Map<Integer, Float> distanceMeters = new HashMap<>();
    private boolean sortedByDistance = false;      // 是否已经拿到定位、按距离排序
    private Location lastLocation;                 // 最近一次定位点（服务器数据刷新后要重算距离）

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载首页布局
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 数据准备：档口 + 菜品都从 assets 的 json 读（离线的"货单"）
        allShops = JsonParser.parseShops(requireContext());
        allDishes = JsonParser.parseDishes(requireContext());

        // 列表标题控件先拿到手：下面恢复"外卖/自取"模式时会立刻刷新列表，
        // 刷新里要改标题，不能等后面再 findViewById（否则空指针）
        tvStallTitle = view.findViewById(R.id.tv_stall_title);

        // 地图入口：打开校园地图（高德 SDK），看各食堂位置与实时距离
        view.findViewById(R.id.btn_open_map).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MapActivity.class)));

        // ① Banner 轮播：按当前时间段生成"时空专区"推荐页
        ViewPager2 viewPagerBanner = view.findViewById(R.id.viewpager_banner);
        viewPagerBanner.setAdapter(new BannerAdapter(buildBannerItems()));

        // ② 档口列表 + 空态占位
        rvStallList = view.findViewById(R.id.rv_stall_list);
        tvEmpty = view.findViewById(R.id.tv_empty);
        rvStallList.setLayoutManager(new LinearLayoutManager(getContext()));
        stallAdapter = new StallAdapter(new ArrayList<>(), shop -> openShopDetail(shop));
        rvStallList.setAdapter(stallAdapter);

        // ③ 搜索框：输入即过滤（按档口名 / 菜品名），本地过滤不联网
        etSearch = view.findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refreshList(); }
        });

        // ④ 外卖/自取切换：换模式就重刷列表，并记住模式
        rgMode = view.findViewById(R.id.rg_mode);
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            takeoutMode = checkedId == R.id.rb_takeout;
            CartStore.setMode(requireContext(),
                    takeoutMode ? CartStore.MODE_TAKEOUT : CartStore.MODE_DINEIN);
            refreshList();
        });
        // 启动时恢复上次选择的模式
        takeoutMode = CartStore.isTakeout(requireContext());
        rgMode.check(takeoutMode ? R.id.rb_takeout : R.id.rb_dinein);

        // ⑤ 分类入口：点"早餐/套餐/轻食/饮品"进分类页（跨食堂展示该分类菜品）
        view.findViewById(R.id.ll_cat_breakfast)
                .setOnClickListener(v -> openCategory(getString(R.string.cat_breakfast)));
        view.findViewById(R.id.ll_cat_meal)
                .setOnClickListener(v -> openCategory(getString(R.string.cat_meal)));
        view.findViewById(R.id.ll_cat_light)
                .setOnClickListener(v -> openCategory(getString(R.string.cat_light)));
        view.findViewById(R.id.ll_cat_drink)
                .setOnClickListener(v -> openCategory(getString(R.string.cat_drink)));

        // ⑥ 标题动态显示当前"时空专区"名（时空适配的可视入口）
        updateTitle();

        refreshList();

        // ⑦ 定位：算出到每个食堂的实时距离，列表按由近到远推荐
        initLocation();

        // ⑧ 后台同步服务器数据：成功就换成服务器最新档口/菜品，失败静默用本地数据
        DataSync.sync(requireContext(), this);
    }

    // ---------- 定位：各食堂实时距离 ----------

    private void initLocation() {
        locationHelper = new LocationHelper(requireContext(),
                new LocationHelper.OnLocationResultListener() {
                    @Override
                    public void onLocation(Location location) {
                        useRealCampus(location);
                    }

                    @Override
                    public void onLocationUnavailable() {
                        // 还没有坐标：什么都不做，列表先按默认顺序展示
                    }
                });

        if (LocationHelper.hasPermission(requireContext())) {
            locationHelper.start();
        } else {
            // 只在第一次进首页时弹权限框；拒绝过就不再反复骚扰
            SharedPreferences sp = requireContext()
                    .getSharedPreferences(PREF_LOCATION, Context.MODE_PRIVATE);
            if (!sp.getBoolean(KEY_LOCATION_ASKED, false)) {
                sp.edit().putBoolean(KEY_LOCATION_ASKED, true).apply();
                requestLocationPermission();
            }
        }
    }

    /** 申请定位权限（精确 + 粗略一起申请，任一通过都能算出大致距离） */
    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        if (LocationHelper.hasPermission(requireContext())) {
            locationHelper.start();
        }
    }

    private void useRealCampus(Location location) {
        // 系统定位可能是 WGS-84，也可能（国产手机的网络定位）已经是 GCJ-02，统一交给 GeoUtil 判断
        double[] mapPoint = GeoUtil.toGcj(requireContext(), location);
        Location mapLoc = new Location(location);
        mapLoc.setLatitude(mapPoint[0]);
        mapLoc.setLongitude(mapPoint[1]);
        lastLocation = mapLoc;

        // 食堂坐标是固定的（存在 shops.json 里），距离只用"当前位置 + 食堂坐标"计算，
        // 不再用 POI 搜索去改写坐标，避免同名食堂匹配到校外的错误位置
        updateDistances(mapLoc);

        // 顺手刷新校区中心点缓存（外卖选送达地址时要用它当搜索圆心）
        if (CampusPoiStore.needsSearch(requireContext())) {
            CampusPoiStore.refresh(requireContext(), mapPoint[0], mapPoint[1], null);
        }
    }

    /**
     * 用当前位置算出到每个食堂的直线距离，再刷新列表。
     * Location.distanceBetween() 内部用的是球面三角公式，单位米。
     */
    private void updateDistances(Location here) {
        distanceMeters.clear();
        float[] meters = new float[1];
        StringBuilder log = new StringBuilder("here=" + here.getLatitude() + ","
                + here.getLongitude() + " ");
        for (Shop shop : allShops) {
            Location.distanceBetween(here.getLatitude(), here.getLongitude(),
                    shop.getLatitude(), shop.getLongitude(), meters);
            log.append(shop.getName()).append('=')
                    .append(Math.round(meters[0])).append('m');
            // 超过 50 公里说明定位根本不在校区附近，宁可不显示距离
            if (meters[0] <= MAX_SHOW_DISTANCE_M) {
                distanceMeters.put(shop.getShopId(), meters[0]);
            }
        }
        android.util.Log.d("LocationHelper", log.toString());
        sortedByDistance = true;
        refreshList(); // 重新排序 + 刷新每张卡片上的距离
    }

    /** 距离文案：1 公里内用米（取整），超过用公里（保留一位小数） */
    private String formatDistance(float meters) {
        if (meters < 1000) {
            return getString(R.string.loc_distance_m, Math.round(meters));
        }
        return getString(R.string.loc_distance_km, meters / 1000f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 页面销毁时释放定位资源，避免回调打到已销毁的界面
        if (locationHelper != null) {
            locationHelper.destroy();
        }
        DataSync.removeListener(this);
    }

    /** 服务器数据同步结果：成功就重读数据并刷新列表，失败静默（继续用本地演示数据） */
    @Override
    public void onSyncResult(boolean success) {
        if (!success || getView() == null) {
            return;
        }
        allShops = JsonParser.parseShops(requireContext());
        allDishes = JsonParser.parseDishes(requireContext());
        if (lastLocation != null) {
            updateDistances(lastLocation); // 重新算距离 + 刷新列表
        } else {
            refreshList();
        }
    }

    /** 取当前北京时间的小时数（模拟器/设备默认时区可能是 UTC，统一按北京时间判断时段） */
    private int beijingHour() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private String currentZoneName() {
        int hour = beijingHour();
        if (hour >= 6 && hour < 9) return getString(R.string.zone_breakfast);   // 6-9 早餐
        if (hour >= 11 && hour < 13) return getString(R.string.zone_lunch);     // 11-13 快取
        if (hour >= 17 && hour < 19) return getString(R.string.zone_dinner);    // 17-19 晚餐
        return getString(R.string.zone_popular);                                // 其他：人气
    }

    private int topShopIdForZone() {
        String tag;
        int hour = beijingHour();
        if (hour >= 6 && hour < 9) {
            tag = getString(R.string.cat_breakfast);       // 早餐
        } else if (hour >= 11 && hour < 13) {
            tag = getString(R.string.cat_meal);            // 套餐
        } else if (hour >= 17 && hour < 19) {
            tag = getString(R.string.cat_light);           // 轻食
        } else {
            return 0;
        }
        for (Shop shop : allShops) {
            if (shop.getTags() != null && shop.getTags().contains(tag)) {
                return shop.getShopId();
            }
        }
        return 0;
    }

    /** 生成 3 页 Banner：主推档口排最前，每页 = 专区名 + 档口名 + 一道招牌菜 */
    private List<BannerAdapter.BannerItem> buildBannerItems() {
        String zone = currentZoneName();
        List<Shop> ordered = zoneOrderedShops(); // 主推档口提到最前
        List<BannerAdapter.BannerItem> list = new ArrayList<>();
        int[] colors = {
                getResources().getColor(R.color.banner_1),
                getResources().getColor(R.color.banner_2),
                getResources().getColor(R.color.banner_3)
        };
        for (int i = 0; i < ordered.size() && i < 3; i++) {
            Shop shop = ordered.get(i);
            list.add(new BannerAdapter.BannerItem(colors[i],
                    zone + " · " + shop.getName() + "：" + firstDishName(shop.getShopId())));
        }
        return list;
    }

    /** 把"主推档口"提到最前，其余保持原顺序（列表里也可见到时空推荐） */
    private List<Shop> zoneOrderedShops() {
        List<Shop> list = new ArrayList<>(allShops);
        int topId = topShopIdForZone();
        if (topId > 0) {
            list.sort((a, b) -> {
                boolean aTop = a.getShopId() == topId;
                boolean bTop = b.getShopId() == topId;
                if (aTop && !bTop) return -1;
                if (!aTop && bTop) return 1;
                return 0;
            });
        }
        return list;
    }

    /** 查该档口第一道菜名，给 Banner 当推荐副标题 */
    private String firstDishName(int shopId) {
        for (Dish dish : allDishes) {
            if (dish.getShopId() == shopId) return dish.getName();
        }
        return "";
    }

    // ---------- 列表刷新（模式过滤 + 搜索过滤） ----------

    /**
     * 统一刷新列表：第一步按外卖/自取过滤，第二步按搜索关键字过滤。
     * 搜索匹配规则：档口名包含关键字，或该档口某道菜名包含关键字。
     */
    private void refreshList() {
        String keyword = etSearch.getText().toString().trim();

        // 第一步：按模式过滤（自取=全部；外卖=只留支持外卖的档口）
        List<Shop> base = new ArrayList<>();
        for (Shop shop : allShops) {
            if (!takeoutMode || shop.isSupportTakeout()) {
                base.add(shop);
            }
        }

        // 第二步：按关键字过滤
        List<Shop> result = new ArrayList<>();
        if (keyword.isEmpty()) {
            result.addAll(base); // 没输入就全显示
        } else {
            for (Shop shop : base) {
                if (shop.getName().contains(keyword)) {           // 档口名命中
                    result.add(shop);
                    continue;
                }
                for (Dish dish : allDishes) {                     // 菜品名命中
                    if (dish.getShopId() == shop.getShopId()
                            && dish.getName().contains(keyword)) {
                        result.add(shop);
                        break;
                    }
                }
            }
        }

        // 第三步：有实时距离就按"由近到远"排，把最近的食堂推荐到最上面
        if (sortedByDistance && !distanceMeters.isEmpty()) {
            result.sort((a, b) -> {
                Float da = distanceMeters.get(a.getShopId());
                Float db = distanceMeters.get(b.getShopId());
                if (da == null && db == null) return 0;
                if (da == null) return 1;   // 没距离的排最后
                if (db == null) return -1;
                return Float.compare(da, db);
            });
        }

        stallAdapter.replaceData(result);

        // 把米数转成"距你 xxx m / km"显示到卡片上（没有定位就清空，卡片自动隐藏）
        Map<Integer, String> texts = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : distanceMeters.entrySet()) {
            texts.put(entry.getKey(), formatDistance(entry.getValue()));
        }
        stallAdapter.setDistances(texts);

        updateTitle();
        updateEmptyView(keyword);
    }

    /** 列表标题：拿到定位后加一句"由近到远"，让用户知道这是按距离推荐的 */
    private void updateTitle() {
        tvStallTitle.setText(sortedByDistance
                ? getString(R.string.home_zone_title_near, currentZoneName())
                : getString(R.string.home_zone_title, currentZoneName()));
    }

    /** 空态：区分"本来就没档口"和"搜索无结果"两种提示 */
    private void updateEmptyView(String keyword) {
        boolean empty = stallAdapter.getItemCount() == 0;
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvStallList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            tvEmpty.setText(keyword.isEmpty()
                    ? R.string.home_stall_empty
                    : R.string.search_empty);
        }
    }

    /** 打开档口详情页：把档口名 + id 用 Intent "快递包" 带过去 */
    private void openShopDetail(Shop shop) {
        Intent intent = new Intent(requireContext(), StallDetailActivity.class);
        intent.putExtra("shop_name", shop.getName());
        intent.putExtra("shop_id", shop.getShopId());
        startActivity(intent);
    }

    /** 打开分类页：把"分类名"带过去，由 CategoryActivity 按它筛选菜品 */
    private void openCategory(String category) {
        Intent intent = new Intent(requireContext(), CategoryActivity.class);
        intent.putExtra("category", category);
        startActivity(intent);
    }
}
