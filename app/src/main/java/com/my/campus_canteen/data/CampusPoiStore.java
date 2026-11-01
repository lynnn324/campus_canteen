package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.my.campus_canteen.model.CampusPoi;
import com.my.campus_canteen.model.Shop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CampusPoiStore {

    private static final String PREF = "campus_poi_pref";
    private static final String KEY_CANTEENS = "canteens";   // 食堂列表 JSON
    private static final String KEY_BUILDINGS = "buildings"; // 教学楼列表 JSON
    private static final String KEY_LAT = "anchor_lat";      // 搜索时的中心点纬度
    private static final String KEY_LNG = "anchor_lng";      // 搜索时的中心点经度
    private static final String KEY_TIME = "search_time";    // 搜索时间戳
    private static final String KEY_ATTEMPT = "try_time";    // 上次尝试搜索的时间戳
    private static final String KEY_SHOP_GEO = "shop_geo";   // 每个档口对应的真实 POI 坐标
    private static final String KEY_SHOP_TIME = "shop_time"; // 档口位置搜索时间戳
    private static final String KEY_SHOP_TRY = "shop_try";   // 上次尝试搜索档口位置的时间戳

    private static final long FRESH_MS = 6 * 60 * 60 * 1000L; // 6 小时内不重复搜
    private static final long RETRY_MS = 10 * 60 * 1000L;     // 失败后 10 分钟内不重试
    private static final int RADIUS = 1500;                   // 搜周边 1.5 公里（大致覆盖一个校区）
    private static final int PAGE_SIZE = 25;                  // 每类最多取 25 个
    private static final int SHOP_RADIUS = 5000;              // 按档口名搜索半径：整个校区都在范围内
    // 搜到的真实 POI 离档口原始坐标超过这个距离，就当作同名误匹配（可能是别家校区的食堂）
    private static final float SHOP_GEO_MAX_OFFSET_M = 300f;
    private static final long TIMEOUT_MS = 8000;              // 搜索兜底超时
    private static final long SHOP_TIMEOUT_MS = 90000;        // 档口逐个串行搜，给足时间

    private static boolean searching = false; // 防止并发重复发起
    private static boolean resolving = false; // 档口位置搜索是否正在进行

    private CampusPoiStore() {
        // 工具类：只允许静态调用
    }

    /** 搜索完成的回调（在主线程执行，可直接刷新界面） */
    public interface Listener {
        void onReady();
    }

    /** 内部：单个关键词的搜索结果回调 */
    private interface OnList {
        void onList(List<CampusPoi> list);
    }

    /** 内部：单个档口名字的搜索结果回调（找不到传 null） */
    private interface OnShop {
        void onShop(CampusPoi poi);
    }

    /** 内部：一个搜索关键词里最像的那条结果 + 它的匹配分数 */
    private interface OnCandidate {
        void onCandidate(CampusPoi poi, int score);
    }

    // ---------- 发起搜索 ----------

    public static void refresh(Context context, double lat, double lng, Listener listener) {
        if (searching) return; // 已经在搜了，不重复发请求

        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final List<CampusPoi> canteens = new ArrayList<>();
        final List<CampusPoi> buildings = new ArrayList<>();
        final int[] pending = {2};      // 两个关键词，各回一次
        final boolean[] finished = {false};

        // 全部完成（或超时兜底）：写缓存 → 回主线程通知界面
        Runnable complete = () -> {
            if (finished[0]) return;
            finished[0] = true;
            searching = false;
            if (!canteens.isEmpty() || !buildings.isEmpty()) {
                save(app, lat, lng, canteens, buildings);
            }
            if (listener != null) {
                main.post(listener::onReady);
            }
        };
        // 兜底：某个搜索没回调时，8 秒后也把已有结果用起来
        main.postDelayed(complete, TIMEOUT_MS);

        searching = true;
        sp(app).edit().putString(KEY_ATTEMPT, String.valueOf(System.currentTimeMillis())).apply();
        searchByKeyword(app, lat, lng, "食堂", CampusPoi.TYPE_CANTEEN, list -> {
            canteens.addAll(list);
            if (--pending[0] <= 0) complete.run();
        });
        searchByKeyword(app, lat, lng, "教学楼", CampusPoi.TYPE_BUILDING, list -> {
            buildings.addAll(list);
            if (--pending[0] <= 0) complete.run();
        });
    }

    /** 用一个关键词做"周边搜索"，把结果转成 CampusPoi 列表 */
    private static void searchByKeyword(Context c, double lat, double lng, String keyword,
                                        String type, OnList callback) {
        try {
            PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
            query.setPageSize(PAGE_SIZE);
            query.setPageNum(0);
            query.setDistanceSort(true); // 按离我远近排序

            PoiSearch search = new PoiSearch(c, query);
            search.setBound(new PoiSearch.SearchBound(new LatLonPoint(lat, lng), RADIUS, true));
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult result, int rCode) {
                    List<CampusPoi> list = new ArrayList<>();
                    if (rCode == AMapException.CODE_AMAP_SUCCESS
                            && result != null && result.getPois() != null) {
                        for (PoiItem item : result.getPois()) {
                            LatLonPoint p = item.getLatLonPoint();
                            String title = item.getTitle();
                            if (p == null || title == null) continue;
                            if (!isRelevant(keyword, title)) continue; // 过滤掉名字不沾边的店
                            list.add(new CampusPoi(title, p.getLatitude(), p.getLongitude(), type));
                        }
                    }
                    sortByCampusThenDistance(list, lat, lng); // 本校的排前面，再按远近
                    callback.onList(list); // 失败也回调空列表，让流程继续
                }

                @Override
                public void onPoiItemSearched(PoiItem item, int rCode) {
                    // 详情搜索用不到，留空
                }
            });
            search.searchPOIAsyn();
        } catch (Exception e) {
            callback.onList(new ArrayList<>()); // SDK 异常时兜底，不让 App 崩
        }
    }

    /**
     * 名字相关性过滤：高德关键词搜索会把名字/地址里带关键词的小店也返回，
     * 这里只留真正像"食堂"或"教学楼"的，避免把奶茶店当成食堂挂到档口上。
     */
    private static boolean isRelevant(String keyword, String title) {
        if ("食堂".equals(keyword)) {
            return title.contains("食堂") || title.contains("餐厅") || title.contains("饭堂")
                    || title.contains("美食城") || title.contains("美食广场");
        }
        return title.contains("教学"); // 教学楼 / 教学大楼
    }

    /** 排序：名字里有本校（湖南科技大学/科大）的排前面，同类再按离搜索中心的距离由近到远 */
    private static void sortByCampusThenDistance(List<CampusPoi> list, final double lat, final double lng) {
        final Map<CampusPoi, Float> meters = new HashMap<>();
        float[] buf = new float[1];
        for (CampusPoi poi : list) {
            Location.distanceBetween(lat, lng, poi.getLatitude(), poi.getLongitude(), buf);
            meters.put(poi, buf[0]);
        }
        list.sort((a, b) -> {
            boolean aCampus = isOurCampus(a.getName());
            boolean bCampus = isOurCampus(b.getName());
            if (aCampus != bCampus) return aCampus ? -1 : 1; // 本校优先
            return Float.compare(meters.get(a), meters.get(b));
        });
    }

    /** 是否本校名称（湖南科技大学校园内的 POI 通常带校名或"科大"简称） */
    private static boolean isOurCampus(String name) {
        return name.contains("湖南科技大学") || name.contains("科大");
    }

    // ---------- 读缓存 ----------

    /** 缓存是否还新鲜（6 小时内且已有食堂数据） */
    public static boolean isFresh(Context c) {
        long time = parseLong(sp(c).getString(KEY_TIME, "0"));
        boolean recent = System.currentTimeMillis() - time < FRESH_MS;
        return recent && !canteens(c).isEmpty();
    }

    public static boolean needsSearch(Context c) {
        if (isFresh(c)) return false;
        long attempt = parseLong(sp(c).getString(KEY_ATTEMPT, "0"));
        return System.currentTimeMillis() - attempt > RETRY_MS;
    }

    /** 缓存里搜索时的中心点（纬度, 经度），没有返回 null */
    public static double[] anchor(Context c) {
        String lat = sp(c).getString(KEY_LAT, "");
        String lng = sp(c).getString(KEY_LNG, "");
        if (lat.isEmpty() || lng.isEmpty()) return null;
        try {
            return new double[]{Double.parseDouble(lat), Double.parseDouble(lng)};
        } catch (Exception e) {
            return null;
        }
    }

    public static List<CampusPoi> canteens(Context c) {
        return loadList(sp(c).getString(KEY_CANTEENS, ""), CampusPoi.TYPE_CANTEEN);
    }

    public static List<CampusPoi> buildings(Context c) {
        return loadList(sp(c).getString(KEY_BUILDINGS, ""), CampusPoi.TYPE_BUILDING);
    }

    // ---------- 档口 → 真实位置 ----------

    public static void applyShopGeo(Context c, List<Shop> shops) {
        if (shops == null || shops.isEmpty()) return;
        JSONObject cache = readShopGeo(c);
        for (Shop shop : shops) {
            JSONObject o = cache.optJSONObject(String.valueOf(shop.getShopId()));
            if (o == null) continue;
            double lat = o.optDouble("lat", shop.getLatitude());
            double lng = o.optDouble("lng", shop.getLongitude());
            // 缓存里的坐标离档口本身太远，多半是搜到了同名但不在本校的食堂
            // （例如"二食堂"匹配到隔壁学校），这种情况保留 shops.json 里的原始坐标
            if (distanceMeters(shop.getLatitude(), shop.getLongitude(), lat, lng)
                    > SHOP_GEO_MAX_OFFSET_M) {
                continue;
            }
            shop.setGeo(lat, lng);
        }
    }

    /** 两点间直线距离（米） */
    private static float distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        float[] buf = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, buf);
        return buf[0];
    }

    public static void resolveShopGeo(Context c, List<Shop> shops, double lat, double lng,
                                      Listener listener) {
        if (shops == null || shops.isEmpty()) {
            if (listener != null) listener.onReady();
            return;
        }
        if (resolving) return; // 正在搜，别重复发请求
        if (isShopGeoFresh(c)) { // 6 小时内不再重搜，坐标定下来就不动
            if (listener != null) listener.onReady();
            return;
        }
        long attempt = parseLong(sp(c).getString(KEY_SHOP_TRY, "0"));
        if (System.currentTimeMillis() - attempt < RETRY_MS) return; // 刚试过，别连发

        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final JSONObject found = readShopGeo(app); // 已定下的档口坐标，原样保留

        // 关键：只搜缓存里还没有的档口。
        // 已搜到的档口若每次重搜，同名 POI 可能换成另一家（外卖柜、门口小店），
        // 坐标一变首页距离就跟着跳，所以已定下的坐标永不覆盖。
        List<Shop> missing = new ArrayList<>();
        for (Shop shop : shops) {
            if (!found.has(String.valueOf(shop.getShopId()))) missing.add(shop);
        }
        if (missing.isEmpty()) { // 全部都有：只刷新时间戳，不再发请求
            saveShopGeo(app, found);
            if (listener != null) listener.onReady();
            return;
        }

        final boolean[] finished = {false};
        Runnable complete = () -> {
            if (finished[0]) return;
            finished[0] = true;
            resolving = false;
            if (found.length() > 0) {
                saveShopGeo(app, found); // 搜不到的就先用 shops.json 兜底，6 小时后才再试
            }
            if (listener != null) main.post(listener::onReady);
        };
        // 兜底：个别搜索没回调时，到点也把已有结果用起来
        main.postDelayed(complete, SHOP_TIMEOUT_MS);

        resolving = true;
        sp(app).edit().putString(KEY_SHOP_TRY, String.valueOf(System.currentTimeMillis())).apply();

        // 一个一个搜：高德对并发请求有限流，同时打十几个会有几个直接失败
        resolveOne(app, lat, lng, missing, 0, found, complete);
    }

    /** 串行搜索第 index 个档口；主名不行就换别名，搜完接着下一个 */
    private static void resolveOne(Context c, double lat, double lng, List<Shop> shops, int index,
                                   JSONObject found, Runnable done) {
        if (index >= shops.size()) {
            done.run();
            return;
        }
        Shop shop = shops.get(index);
        queryShop(c, lat, lng, shop.getName(), (poi, score) -> {
            if (poi != null && score > 0) {
                putShopGeo(found, shop, poi);
                resolveOne(c, lat, lng, shops, index + 1, found, done);
                return;
            }
            // 主名搜不到（如"二至坊"），换个说法（如"二区食堂"）再问一次
            searchShopByName(c, lat, lng, aliasesFor(shop), aliasPoi -> {
                if (aliasPoi != null) putShopGeo(found, shop, aliasPoi);
                resolveOne(c, lat, lng, shops, index + 1, found, done);
            });
        });
    }

    /** 把一条搜到的高德 POI 记到缓存 JSON 里（档口编号 → 坐标 + POI 名） */
    private static void putShopGeo(JSONObject geo, Shop shop, CampusPoi poi) {
        try {
            // 搜到的位置离档口原始坐标太远，说明匹配错了（同名别家食堂），不写入缓存
            if (distanceMeters(shop.getLatitude(), shop.getLongitude(),
                    poi.getLatitude(), poi.getLongitude()) > SHOP_GEO_MAX_OFFSET_M) {
                return;
            }
            JSONObject o = new JSONObject();
            o.put("lat", poi.getLatitude());
            o.put("lng", poi.getLongitude());
            o.put("poi", poi.getName());
            geo.put(String.valueOf(shop.getShopId()), o);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String[] aliasesFor(Shop shop) {
        switch (shop.getShopId()) {
            case 2:
                return new String[]{"二区食堂", "二食堂"};
            case 4:
                return new String[]{"五食堂", "五味苑食堂"};
            case 5:
                return new String[]{"六食堂", "六和堂食堂"};
            case 9:
                return new String[]{"学术交流中心", "教工餐厅"};
            default:
                return new String[0];
        }
    }

    /** 依次尝试一个档口的多个别名，找到第一个就停 */
    private static void searchShopByName(Context c, double lat, double lng, String[] names,
                                         OnShop callback) {
        tryNextAlias(c, lat, lng, names, 0, callback);
    }

    private static void tryNextAlias(Context c, double lat, double lng, String[] names, int index,
                                     OnShop callback) {
        if (index >= names.length) {
            callback.onShop(null);
            return;
        }
        queryShop(c, lat, lng, names[index], (poi, score) -> {
            if (poi != null && score > 0) {
                callback.onShop(poi);
            } else {
                tryNextAlias(c, lat, lng, names, index + 1, callback);
            }
        });
    }

    /** 用一个关键词做一次周边搜索，返回名字最像的那条 POI 和分数 */
    private static void queryShop(Context c, double lat, double lng, String name,
                                  OnCandidate callback) {
        try {
            PoiSearch.Query query = new PoiSearch.Query(name, "", "");
            query.setPageSize(20);
            query.setPageNum(0);
            query.setDistanceSort(true); // 同名的有多家时，先看离我近的

            PoiSearch search = new PoiSearch(c, query);
            search.setBound(new PoiSearch.SearchBound(
                    new LatLonPoint(lat, lng), SHOP_RADIUS, true));
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult result, int rCode) {
                    CampusPoi best = null;
                    int bestScore = 0;
                    if (rCode == AMapException.CODE_AMAP_SUCCESS
                            && result != null && result.getPois() != null) {
                        for (PoiItem item : result.getPois()) {
                            LatLonPoint p = item.getLatLonPoint();
                            String title = item.getTitle();
                            if (p == null || title == null) continue;
                            int score = nameScore(name, title);
                            if (score <= bestScore) continue; // 名字不像或不如已找到的
                            bestScore = score;
                            best = new CampusPoi(title, p.getLatitude(), p.getLongitude(),
                                    CampusPoi.TYPE_CANTEEN);
                        }
                    }
                    callback.onCandidate(best, bestScore); // 失败也回调，让流程继续
                }

                @Override
                public void onPoiItemSearched(PoiItem item, int rCode) {
                    // 详情搜索用不到，留空
                }
            });
            search.searchPOIAsyn();
        } catch (Exception e) {
            callback.onCandidate(null, 0); // SDK 异常时兜底，不让 App 崩
        }
    }

    /**
     * 给 POI 名字打分：越像"这个食堂本身"分越高，
     * 避免把食堂门口的外卖柜、同名小店当成食堂本体。
     * 100：名字就是店名；95：带校名前缀的完整名；80：校名开头；60：含店名且是食堂/餐厅；30：仅含店名。
     */
    private static int nameScore(String name, String title) {
        // 外卖柜/取餐柜只是摆在食堂门口的一排柜子，不能代表食堂位置
        if (title.contains("外卖柜") || title.contains("取餐柜")
                || title.contains("自提柜") || title.contains("快递")) {
            return 0;
        }
        if (title.equals(name)) return 100;
        if (title.equals("湖南科技大学" + name) || title.equals("湖南科技大学-" + name)
                || title.equals("科大" + name)) return 95;
        if (!title.contains(name)) return 0;
        if (title.startsWith("湖南科技大学") || title.startsWith("科大")) return 80;
        if (title.contains("食堂") || title.contains("餐厅")) return 60;
        return 30;
    }

    /** 档口真实坐标缓存是否还新鲜（6 小时内且已有结果） */
    private static boolean isShopGeoFresh(Context c) {
        long time = parseLong(sp(c).getString(KEY_SHOP_TIME, "0"));
        boolean recent = System.currentTimeMillis() - time < FRESH_MS;
        return recent && readShopGeo(c).length() > 0;
    }

    /** 读档口坐标缓存：档口编号 → {lat,lng,poi} */
    private static JSONObject readShopGeo(Context c) {
        String json = sp(c).getString(KEY_SHOP_GEO, "");
        if (json == null || json.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject(); // 数据坏了当没有，不崩溃
        }
    }

    private static void saveShopGeo(Context c, JSONObject geo) {
        sp(c).edit()
                .putString(KEY_SHOP_GEO, geo.toString())
                // 这一轮搜完就记账：6 小时内不再重搜，距离不会被反复改写的坐标带得乱跳
                .putString(KEY_SHOP_TIME, String.valueOf(System.currentTimeMillis()))
                .apply();
    }

    // ---------- 存储细节 ----------

    /** 写缓存：把两个列表和搜索中心点存进小本子 */
    private static void save(Context c, double lat, double lng,
                             List<CampusPoi> canteens, List<CampusPoi> buildings) {
        sp(c).edit()
                .putString(KEY_CANTEENS, toJson(canteens))
                .putString(KEY_BUILDINGS, toJson(buildings))
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LNG, String.valueOf(lng))
                .putString(KEY_TIME, String.valueOf(System.currentTimeMillis()))
                .apply();
    }

    private static String toJson(List<CampusPoi> list) {
        JSONArray arr = new JSONArray();
        for (CampusPoi poi : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", poi.getName());
                o.put("lat", poi.getLatitude());
                o.put("lng", poi.getLongitude());
                arr.put(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return arr.toString();
    }

    private static List<CampusPoi> loadList(String json, String type) {
        List<CampusPoi> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new CampusPoi(o.getString("name"),
                        o.getDouble("lat"), o.getDouble("lng"), type));
            }
        } catch (Exception e) {
            e.printStackTrace(); // 数据坏了当没有，不崩溃
        }
        return list;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
