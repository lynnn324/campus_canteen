package com.my.campus_canteen.data;

import android.content.Context;

import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.model.Shop;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonParser {

    private JsonParser() {
    }

    /**
     * 解析"档口"：优先读服务器同步下来的缓存（filesDir/remote/shops.json），
     * 缓存没有或解析失败时回落到 assets 里的演示数据 → List<Shop>
     */
    public static List<Shop> parseShops(Context context) {
        List<Shop> list = parseShopsText(DataSync.readCache(context, "shops.json"));
        if (!list.isEmpty()) {
            return list;
        }
        try {
            return parseShopsText(readAssets(context, "shops.json"));
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>(); // 出错时返回空列表，界面显示"暂无档口"，不会崩
        }
    }

    private static List<Shop> parseShopsText(String json) {
        List<Shop> list = new ArrayList<>();
        if (json == null) {
            return list;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.getJSONArray("shops");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);

                // 把 tags 数组再拆成 List<String>
                JSONArray tagsArr = o.getJSONArray("tags");
                List<String> tags = new ArrayList<>();
                for (int j = 0; j < tagsArr.length(); j++) {
                    tags.add(tagsArr.getString(j));
                }

                Shop shop = new Shop(
                        o.getInt("shopId"),
                        o.getString("name"),
                        tags,
                        o.getString("intro"),
                        o.getBoolean("supportTakeout"),
                        o.getInt("deliveryFee"),
                        // 起送价 / 月售：后加字段，老数据缺失给一个合理默认值，卡片照常显示
                        o.has("minPrice") ? o.getInt("minPrice") : 15,
                        o.has("monthlySales") ? o.getInt("monthlySales") : 0,
                        o.getInt("estimatedTime"),
                        // 经纬度是后加字段：老数据缺失就存 0，距离显示自然兜底
                        o.has("latitude") ? o.getDouble("latitude") : 0,
                        o.has("longitude") ? o.getDouble("longitude") : 0
                );
                list.add(shop);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<>(); // 解析失败返回空表，调用方会回落到 assets
        }
        return list;
    }

    /** 解析"菜品"：优先读服务器缓存，其次 assets 演示数据 → List<Dish> */
    public static List<Dish> parseDishes(Context context) {
        List<Dish> list = parseDishesText(DataSync.readCache(context, "dishes.json"));
        if (!list.isEmpty()) {
            return list;
        }
        try {
            return parseDishesText(readAssets(context, "dishes.json"));
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>(); // 界面显示"暂无菜品"，不会崩
        }
    }

    private static List<Dish> parseDishesText(String json) {
        List<Dish> list = new ArrayList<>();
        if (json == null) {
            return list;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.getJSONArray("dishes");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Dish(
                        o.getInt("dishId"),
                        o.getInt("shopId"),
                        o.getString("name"),
                        o.getString("type"),
                        // category 是后加的字段：老数据没有就存空串，兼容不崩
                        o.has("category") ? o.getString("category") : "",
                        o.getDouble("price"),
                        o.getBoolean("smallPortionWarn"),
                        o.getBoolean("canTakeout"),
                        o.getBoolean("canDineIn"),
                        // 趣味标签是可选字段：老数据没有就存空串，界面自动不显示
                        o.has("label") ? o.getString("label") : "",
                        // 一句话描述也是可选字段：缺失时详情页直接不显示这一段
                        o.has("desc") ? o.getString("desc") : "",
                        // 配图资源名是可选字段：老数据没有就存空串，适配器会用通用图标兜底
                        o.has("image") ? o.getString("image") : ""
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        return list;
    }

    /** 读取 assets 下的文本文件，返回字符串 */
    private static String readAssets(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
