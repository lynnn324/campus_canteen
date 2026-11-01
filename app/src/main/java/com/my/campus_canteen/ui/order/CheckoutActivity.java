package com.my.campus_canteen.ui.order;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.my.campus_canteen.MainActivity;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.CampusPoiStore;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.data.DeliverySpots;
import com.my.campus_canteen.data.EstimateCountDown;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.data.OrderStore;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Order;
import com.my.campus_canteen.model.OrderStatus;
import com.my.campus_canteen.model.Shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CheckoutActivity extends AppCompatActivity {

    private TextView tvDelivery;
    private TextView tvPickTime;
    private TextView tvTotal;
    private TextView btnChooseTime;
    private LinearLayout llItems;
    private TextView tvDeliverySpot;
    private DeliverySpots.Spot deliverySpot; // 当前选中的送达位置（仅外卖）

    private List<CartItem> items;
    private boolean takeout;
    private int deliveryFee;           // 配送费合计（仅外卖）
    private int etaMinutes;            // 预计送达分钟（仅外卖）
    private CountDownTimer countDown;  // 出餐倒计时（自取），离开页面要取消

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // 标题栏返回箭头：点一下回到购物车
        findViewById(R.id.btn_checkout_back).setOnClickListener(v -> finish());

        tvDelivery = findViewById(R.id.tv_checkout_delivery);
        tvPickTime = findViewById(R.id.tv_checkout_pick_time);
        tvTotal = findViewById(R.id.tv_checkout_total);
        btnChooseTime = findViewById(R.id.btn_choose_time);
        llItems = findViewById(R.id.ll_checkout_items);
        tvDeliverySpot = findViewById(R.id.tv_delivery_spot);

        // 读取购物车和模式；购物车空了就直接回去（防止空手结算）
        items = CartStore.load(this);
        if (items.isEmpty()) {
            finish();
            return;
        }
        takeout = CartStore.isTakeout(this);

        // 点餐方式标题
        TextView tvMode = findViewById(R.id.tv_checkout_mode);
        tvMode.setText(getString(R.string.checkout_mode_prefix,
                takeout ? getString(R.string.mode_takeout) : getString(R.string.mode_dinein)));

        // 按模式展示配送/出餐信息
        if (takeout) {
            showTakeoutInfo();
        } else {
            showDineInInfo();
        }

        // 菜品明细 + 合计
        buildItemRows();
        updateTotal();

        // 提交订单
        findViewById(R.id.btn_submit).setOnClickListener(v -> submitOrder());
    }

    /** 外卖模式：配送费 = 各档口配送费之和；送达时间 = 最长的那家 */
    private void showTakeoutInfo() {
        // 从 shops.json 读出档口资料，按 shopId 找配送费/送达时间
        List<Shop> shops = JsonParser.parseShops(this);
        Set<Integer> shopIds = new HashSet<>();
        for (CartItem item : items) {
            shopIds.add(item.getShopId());
        }
        int fee = 0, eta = 0;
        for (Shop shop : shops) {
            if (shopIds.contains(shop.getShopId())) {
                fee += shop.getDeliveryFee(); // 每家的配送费加起来
                eta = Math.max(eta, shop.getEstimatedTime()); // 取最长那家
            }
        }
        deliveryFee = fee;
        etaMinutes = eta;
        tvDelivery.setText(getString(R.string.checkout_delivery_takeout, fee, eta));

        // 外卖不需要选取餐时间，隐藏该区域
        btnChooseTime.setVisibility(View.GONE);
        tvPickTime.setVisibility(View.GONE);

        // 送达位置：沿用上次搜索选中的地点；没选过就显示"未选择"，点"选择"去搜索
        deliverySpot = DeliverySpots.getSelected(this);
        findViewById(R.id.ll_delivery_spot).setVisibility(View.VISIBLE);
        renderDeliverySpot();
        findViewById(R.id.btn_choose_spot).setOnClickListener(v -> chooseDeliverySpot());
    }

    /** 刷新"送达位置：xxx"这一行的文字（还没选过就提示去搜索） */
    private void renderDeliverySpot() {
        if (deliverySpot == null) {
            tvDeliverySpot.setText(R.string.delivery_spot_empty);
        } else {
            tvDeliverySpot.setText(getString(R.string.delivery_spot_prefix, deliverySpot.getName()));
        }
    }

    private void chooseDeliverySpot() {
        View content = getLayoutInflater().inflate(R.layout.dialog_delivery_search, null);
        EditText etKeyword = content.findViewById(R.id.et_spot_keyword);
        Button btnSearch = content.findViewById(R.id.btn_spot_search);
        ProgressBar pbSearch = content.findViewById(R.id.pb_spot_search);
        TextView tvHint = content.findViewById(R.id.tv_spot_hint);
        ListView lvResults = content.findViewById(R.id.lv_spot_results);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delivery_spot_title)
                .setView(content)
                .setNegativeButton(R.string.cancel_keep, null)
                .create();

        // 搜索结果与列表显示用的两份平行数据（下标一一对应）
        List<DeliverySpots.Spot> results = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        SimpleAdapter adapter = new SimpleAdapter(this, rows,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "addr"},
                new int[]{android.R.id.text1, android.R.id.text2});
        lvResults.setAdapter(adapter);

        lvResults.setOnItemClickListener((parent, view, position, id) -> {
            deliverySpot = results.get(position);
            DeliverySpots.setSelected(this, deliverySpot); // 记住，下次下单沿用
            renderDeliverySpot();
            dialog.dismiss();
        });

        btnSearch.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                etKeyword.setError(getString(R.string.delivery_spot_search_hint));
                return;
            }
            searchSpot(keyword, results, rows, adapter, pbSearch, tvHint);
        });
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                btnSearch.performClick();
                return true;
            }
            return false;
        });

        dialog.show();
    }

    private void searchSpot(String keyword, List<DeliverySpots.Spot> results,
                            List<Map<String, String>> rows, SimpleAdapter adapter,
                            ProgressBar pbSearch, TextView tvHint) {
        results.clear();
        rows.clear();
        adapter.notifyDataSetChanged();
        tvHint.setVisibility(View.GONE);
        pbSearch.setVisibility(View.VISIBLE);

        // 以"上次搜到的校区中心"为圆心搜 5 公里；没定位过就按"湘潭"城市范围搜
        double[] anchor = CampusPoiStore.anchor(this);
        if (anchor == null) {
            tvHint.setVisibility(View.VISIBLE);
            tvHint.setText(R.string.delivery_spot_no_location);
        }

        final List<Candidate> pool = new ArrayList<>();
        final boolean[] finished = {false};

        // 统一收尾：去重 → 按"名字像不像"再按距离排序 → 填进下拉列表
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            pbSearch.setVisibility(View.GONE);
            fillResults(pool, keyword, anchor, results, rows);
            adapter.notifyDataSetChanged();
            tvHint.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            if (results.isEmpty()) tvHint.setText(R.string.delivery_spot_no_result);
        };
        // 兜底：某个 SDK 回调丢失时，8 秒后也把已有结果用起来
        new Handler(Looper.getMainLooper()).postDelayed(finish, 8000);

        // 三路请求都回来后决定要不要再走地理编码
        final int[] pending = {0};
        Runnable oneDone = () -> {
            if (--pending[0] > 0) return;
            boolean matched = false;
            for (Candidate c : pool) {
                if (relevance(keyword, c) >= 70) { // 已经命中"带栋/楼编号"的名字
                    matched = true;
                    break;
                }
            }
            if (matched || keyword.contains("湖南科技大学")) {
                finish.run();
                return;
            }
            geocodeSpot("湖南科技大学" + keyword, "湘潭", spot -> {
                if (spot != null) pool.add(spot);
                finish.run();
            });
        };

        pending[0]++;
        searchPoi(keyword, anchor, spots -> { pool.addAll(spots); oneDone.run(); });
        if (looksLikeBuilding(keyword)) {
            pending[0]++;
            searchPoi("湖南科技大学" + keyword, anchor,
                    spots -> { pool.addAll(spots); oneDone.run(); });
        }
        pending[0]++;
        searchTips(keyword, anchor, spots -> { pool.addAll(spots); oneDone.run(); });
    }

    /** 像楼栋/门牌的名字（八区16栋、逸夫楼、学苑3号），才值得再搜一次带校名的完整写法 */
    private boolean looksLikeBuilding(String keyword) {
        return !keyword.contains("湖南科技大学")
                && (keyword.contains("栋") || keyword.contains("楼")
                || keyword.contains("舍") || keyword.contains("号")
                || keyword.contains("区"));
    }

    /** 第一级：高德 POI 关键词搜索（周边 5 公里，按距离排序） */
    private void searchPoi(String keyword, double[] anchor, OnSpots callback) {
        List<Candidate> list = new ArrayList<>();
        try {
            PoiSearch.Query query = new PoiSearch.Query(keyword, "", anchor == null ? "湘潭" : "");
            query.setPageSize(20);
            query.setPageNum(0);
            query.setDistanceSort(true);

            PoiSearch search = new PoiSearch(this, query);
            if (anchor != null) {
                search.setBound(new PoiSearch.SearchBound(
                        new LatLonPoint(anchor[0], anchor[1]), 5000, true));
            }
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult result, int rCode) {
                    if (rCode == AMapException.CODE_AMAP_SUCCESS
                            && result != null && result.getPois() != null) {
                        for (PoiItem item : result.getPois()) {
                            LatLonPoint p = item.getLatLonPoint();
                            if (p == null || item.getTitle() == null) continue;
                            list.add(new Candidate(item.getTitle(),
                                    item.getSnippet() == null ? "" : item.getSnippet(),
                                    p.getLatitude(), p.getLongitude(), item.getTypeCode()));
                        }
                    }
                    callback.onSpots(list); // 失败也回调空列表，让流程继续
                }

                @Override
                public void onPoiItemSearched(PoiItem item, int rCode) {
                    // 详情搜索用不到，留空
                }
            });
            search.searchPOIAsyn();
        } catch (Exception e) {
            callback.onSpots(list); // SDK 异常兜底，不让页面崩
        }
    }

    /** 第二级：输入提示，能返回"八区16栋"这类细粒度地址/楼栋 */
    private void searchTips(String keyword, double[] anchor, OnSpots callback) {
        List<Candidate> list = new ArrayList<>();
        try {
            InputtipsQuery query = new InputtipsQuery(keyword, "湘潭");
            query.setCityLimit(false); // 校区可能跨区，不限制在城市边界内
            if (anchor != null) query.setLocation(new LatLonPoint(anchor[0], anchor[1]));
            Inputtips tips = new Inputtips(this, query);
            tips.setInputtipsListener((tipList, rCode) -> {
                if (rCode == AMapException.CODE_AMAP_SUCCESS && tipList != null) {
                    for (Tip tip : tipList) {
                        // 纯地址提示没有坐标，无法作为送达点，交给第三级地理编码
                        if (tip.getPoint() == null || tip.getName() == null) continue;
                        list.add(new Candidate(tip.getName(),
                                tip.getAddress() == null ? "" : tip.getAddress(),
                                tip.getPoint().getLatitude(), tip.getPoint().getLongitude(),
                                tip.getTypeCode()));
                    }
                }
                callback.onSpots(list);
            });
            tips.requestInputtipsAsyn();
        } catch (Exception e) {
            callback.onSpots(list);
        }
    }

    /** 第三级：地理编码，把"湖南科技大学逸夫楼"解析成具体坐标 */
    private void geocodeSpot(String name, String city, OnSpot callback) {
        try {
            GeocodeSearch geocode = new GeocodeSearch(this);
            geocode.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
                    // 逆地理用不到，留空
                }

                @Override
                public void onGeocodeSearched(GeocodeResult result, int rCode) {
                    Candidate found = null;
                    if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null
                            && result.getGeocodeAddressList() != null) {
                        for (GeocodeAddress address : result.getGeocodeAddressList()) {
                            LatLonPoint p = address.getLatLonPoint();
                            if (p == null) continue;
                            found = new Candidate(name,
                                    address.getFormatAddress() == null ? "" : address.getFormatAddress(),
                                    p.getLatitude(), p.getLongitude(), "");
                            break;
                        }
                    }
                    callback.onSpot(found);
                }
            });
            geocode.getFromLocationNameAsyn(new GeocodeQuery(name, city));
        } catch (Exception e) {
            callback.onSpot(null);
        }
    }

    /** 把候选池去重排序后写进下拉列表（同名或同点只留一条） */
    private void fillResults(List<Candidate> pool, String keyword, double[] anchor,
                             List<DeliverySpots.Spot> results, List<Map<String, String>> rows) {
        List<Candidate> unique = new ArrayList<>();
        for (Candidate c : pool) {
            if (isDining(c)) continue; // 送达位置只要"位置"，食堂/餐厅/档口一律不列出来
            boolean dup = false;
            for (Candidate u : unique) {
                if (u.name.equals(c.name)
                        || (Math.abs(u.lat - c.lat) < 1e-5 && Math.abs(u.lng - c.lng) < 1e-5)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) unique.add(c);
        }

        // 先按"名字像不像"排（带 16栋 的结果压过只带 八区 的），同档再按距离排
        unique.sort((a, b) -> {
            int byScore = Integer.compare(relevance(keyword, b), relevance(keyword, a));
            if (byScore != 0) return byScore;
            if (anchor == null) return 0;
            return Float.compare(metersTo(anchor, a), metersTo(anchor, b));
        });

        for (Candidate c : unique) {
            results.add(new DeliverySpots.Spot(c.name, c.lat, c.lng));
            Map<String, String> row = new HashMap<>();
            row.put("name", c.name);
            row.put("addr", c.addr);
            rows.add(row);
        }
    }

    /**
     * 名字与关键词的相似度：越像用户想找的那栋楼分越高。
     * 100 完全相同 / 98 带校名的完整名 / 92 包含关键词 /
     * 75 含关键词里的编号且带"栋/楼/宿舍"（八区16栋 → 学生宿舍八区16栋）/ 60 只含编号 / 20 其他。
     */
    private int relevance(String keyword, Candidate c) {
        String title = c.name;
        if (title.equals(keyword)) return 100;
        if (title.equals("湖南科技大学" + keyword)
                || title.equals("湖南科技大学-" + keyword)) return 98;
        if (title.contains(keyword)) return 92;
        if (keyword.startsWith("湖南科技大学") && title.contains(keyword.substring(6))) return 90;

        String digits = digitsOf(keyword);
        if (!digits.isEmpty()) {
            String hay = title + (c.addr == null ? "" : c.addr);
            if (hay.contains(digits) && (hay.contains("栋") || hay.contains("楼")
                    || hay.contains("宿舍"))) return 75;
            if (hay.contains(digits)) return 60;
            return 20;
        }
        return 20;
    }

    /** 取出关键词里的数字（八区16栋 → 16），没有数字返回空串 */
    private String digitsOf(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    /**
     * 食堂、餐厅、档口、小吃店这类"吃饭的地方"不能当送达位置，一律过滤。
     * 高德类型码 05 开头就是"餐饮服务"大类，再用名字兜一层（外卖柜、粉店等）。
     */
    private boolean isDining(Candidate c) {
        if (c.typeCode.startsWith("05")) return true;
        String n = c.name;
        String[] words = {"食堂", "餐厅", "饭堂", "美食", "小吃", "档口", "粉店", "面馆",
                "饭店", "食府", "烧烤", "火锅", "奶茶", "咖啡", "外卖柜", "取餐柜"};
        for (String w : words) {
            if (n.contains(w)) return true;
        }
        return false;
    }

    /** 候选点到校区中心的直线距离（米） */
    private float metersTo(double[] anchor, Candidate c) {
        float[] buf = new float[1];
        Location.distanceBetween(anchor[0], anchor[1], c.lat, c.lng, buf);
        return buf[0];
    }

    /** 搜索候选项：名称 + 地址 + 坐标 + 高德类型码 */
    private static class Candidate {
        final String name;
        final String addr;
        final double lat;
        final double lng;
        final String typeCode;

        Candidate(String name, String addr, double lat, double lng, String typeCode) {
            this.name = name;
            this.addr = addr;
            this.lat = lat;
            this.lng = lng;
            this.typeCode = typeCode == null ? "" : typeCode;
        }
    }

    private interface OnSpots {
        void onSpots(List<Candidate> spots);
    }

    private interface OnSpot {
        void onSpot(Candidate spot);
    }

    /** 自取模式：菜品数量估算出餐时间 + CountDownTimer 倒计时 + TimePicker 选取餐时间 */
    private void showDineInInfo() {
        // 按"菜品总份数"估算，而不是购物车行数（点得越多做得越久）
        int totalQty = 0;
        for (CartItem item : items) {
            totalQty += item.getQuantity();
        }
        int minutes = EstimateCountDown.estimateMinutes(totalQty);
        // 启动倒计时，实时刷到 tvDelivery
        countDown = EstimateCountDown.start(minutes, tvDelivery);

        btnChooseTime.setVisibility(View.VISIBLE);
        btnChooseTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (picker, hour, minute) -> {
                String pickTime = String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute);
                tvPickTime.setText(getString(R.string.checkout_pick_time, pickTime));
                tvPickTime.setTag(pickTime); // 存起来，提交订单时写进订单
            }, 12, 30, true).show();
        });
    }

    /** 逐行生成"菜名 xN ¥金额"，塞进明细容器 */
    private void buildItemRows() {
        for (CartItem item : items) {
            TextView row = new TextView(this);
            row.setText(item.getName() + " x" + item.getQuantity()
                    + "    ¥" + (item.getPrice() * item.getQuantity()));
            row.setTextSize(14f);
            row.setPadding(0, 6, 0, 6);
            llItems.addView(row);
        }
    }

    /** 合计：菜品总价 +（外卖时加配送费） */
    private void updateTotal() {
        double total = CartStore.calcTotal(items) + deliveryFee;
        tvTotal.setText(getString(R.string.cart_total, total));
    }

    /** 提交订单：生成 Order → 存进历史 → 清空购物车 → 支付成功 Dialog → 跳订单页 */
    private void submitOrder() {
        // 起送价兜底：店铺页、分类页的购物车入口也能直接进结算，
        // 不能只靠购物车页的按钮拦截，提交前必须再校验一次
        int minPrice = CartStore.getMinPrice(this);
        double goodsTotal = CartStore.calcTotal(items);
        if (goodsTotal < minPrice) {
            Toast.makeText(this, getString(R.string.cart_min_short, minPrice - goodsTotal),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // 外卖必须先搜索并选中送达位置（不再默认给一个预设地址）
        if (takeout && deliverySpot == null) {
            Toast.makeText(this, R.string.delivery_spot_need, Toast.LENGTH_SHORT).show();
            chooseDeliverySpot();
            return;
        }
        double total = CartStore.calcTotal(items) + deliveryFee;
        String pickTime = tvPickTime.getTag() == null ? "" : (String) tvPickTime.getTag();

        Order order = new Order(
                System.currentTimeMillis(), // 时间戳当订单号，唯一
                new ArrayList<>(items),     // 菜品快照
                total,
                takeout ? CartStore.MODE_TAKEOUT : CartStore.MODE_DINEIN,
                OrderStatus.PENDING,        // 新订单一律"待出餐"
                deliveryFee,
                etaMinutes,
                System.currentTimeMillis(),
                pickTime,
                deliverySpot == null ? "" : deliverySpot.getName() // 外卖送达位置快照
        );

        OrderStore.saveOrder(this, order);                  // 存入历史
        CartStore.save(this, new ArrayList<>());            // 购物车清零

        // 支付成功弹窗：确认后跳到订单列表页
        new AlertDialog.Builder(this)
                .setTitle(R.string.pay_success_title)
                .setMessage(R.string.pay_success_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.pay_success_ok, (dialog, which) -> goToOrderTab())
                .show();
    }

    /** 跳到订单列表 Tab（MainActivity 收到 go_order 标记会自动切到订单页） */
    private void goToOrderTab() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("go_order", true);
        // 复用已存在的 MainActivity（不新建一摞页面），并让它处理新 Intent
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDown != null) {
            countDown.cancel();
        }
    }
}
