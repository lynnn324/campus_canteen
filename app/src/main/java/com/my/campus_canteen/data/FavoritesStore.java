package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FavoritesStore {

    private static final String PREFS = "favorite_prefs";   // 小仓库文件名
    private static final String KEY_IDS = "fav_dish_ids";   // 收藏的菜品编号数组

    private FavoritesStore() {
        // 工具类：私有构造，只允许静态调用
    }

    /** 是否已收藏该菜品 */
    public static boolean isFavorite(Context c, int dishId) {
        return loadIds(c).contains(dishId);
    }

    /** 切换收藏状态，返回切换后的结果（true = 已收藏） */
    public static boolean toggle(Context c, int dishId) {
        Set<Integer> ids = loadIds(c);
        boolean nowFav;
        if (ids.contains(dishId)) {
            ids.remove(dishId);   // 再点一次 = 取消收藏
            nowFav = false;
        } else {
            ids.add(dishId);      // 第一次点 = 加入收藏
            nowFav = true;
        }
        saveIds(c, ids);
        return nowFav;
    }

    /** 取消收藏某道菜（收藏列表页用） */
    public static void remove(Context c, int dishId) {
        Set<Integer> ids = loadIds(c);
        ids.remove(dishId);
        saveIds(c, ids);
    }

    /** 收藏数量（"我的"页数据概览用） */
    public static int count(Context c) {
        return loadIds(c).size();
    }

    /** 全部收藏的菜品编号（保持收藏先后顺序） */
    public static List<Integer> getAll(Context c) {
        return new ArrayList<>(loadIds(c));
    }

    /** 读取收藏编号集合：JSON 字符串 → Set<Integer> */
    private static Set<Integer> loadIds(Context c) {
        Set<Integer> ids = new LinkedHashSet<>();
        String json = sp(c).getString(KEY_IDS, "");
        if (json.isEmpty()) return ids; // 空夹子，返回空集合
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                ids.add(arr.getInt(i));
            }
        } catch (Exception e) {
            e.printStackTrace(); // 数据坏了当作没收藏，不崩溃
        }
        return ids;
    }

    /** 保存收藏编号集合 */
    private static void saveIds(Context c, Set<Integer> ids) {
        JSONArray arr = new JSONArray();
        for (Integer id : ids) {
            arr.put(id);
        }
        sp(c).edit().putString(KEY_IDS, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
