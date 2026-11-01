package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Dish;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CartStore {

    // 存储文件 / 键名（SharedPreferences 就是一个 key-value 小仓库）
    private static final String PREFS = "cart_prefs";
    private static final String KEY_CART = "cart_list";   // 购物车 JSON
    private static final String KEY_MODE = "order_mode";  // 点餐模式

    // 模式的取值（用字符串存，简单够用）
    public static final String MODE_DINEIN = "dinein";   // 自取
    public static final String MODE_TAKEOUT = "takeout"; // 外卖

    private CartStore() {
        // 工具类：私有构造，只允许静态调用
    }

    // ---------- 点餐模式 ----------

    /** 读取当前模式（默认自取） */
    public static String getMode(Context c) {
        return sp(c).getString(KEY_MODE, MODE_DINEIN);
    }

    /** 保存当前模式 */
    public static void setMode(Context c, String mode) {
        sp(c).edit().putString(KEY_MODE, mode).apply();
    }

    /** 当前是否是外卖模式 */
    public static boolean isTakeout(Context c) {
        return MODE_TAKEOUT.equals(getMode(c));
    }

    /** 起送价规则：自取无起送价（0 元），外卖 15 元 */
    public static int getMinPrice(Context c) {
        return isTakeout(c) ? 15 : 0;
    }

    // ---------- 购物车 ----------

    /** 读取购物车：JSON 字符串 → List<CartItem> */
    public static List<CartItem> load(Context c) {
        List<CartItem> list = new ArrayList<>();
        String json = sp(c).getString(KEY_CART, "");
        if (json.isEmpty()) return list; // 本子是空的，返回空列表
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new CartItem(
                        o.getInt("dishId"),
                        o.getInt("shopId"),
                        o.getString("shopName"),
                        o.getString("name"),
                        o.getString("type"),
                        o.getDouble("price"),
                        o.getInt("quantity"),
                        o.getBoolean("canTakeout")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace(); // 数据坏了就当没购物车，不崩溃
        }
        return list;
    }

    /** 保存购物车：List<CartItem> → JSON 字符串写进本子 */
    public static void save(Context c, List<CartItem> items) {
        JSONArray arr = new JSONArray();
        for (CartItem item : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("dishId", item.getDishId());
                o.put("shopId", item.getShopId());
                o.put("shopName", item.getShopName());
                o.put("name", item.getName());
                o.put("type", item.getType());
                o.put("price", item.getPrice());
                o.put("quantity", item.getQuantity());
                o.put("canTakeout", item.isCanTakeout());
                arr.put(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sp(c).edit().putString(KEY_CART, arr.toString()).apply();
    }

    /** 加菜：同菜已存在就 +1，不存在就新增一行 */
    public static void add(Context c, Dish dish, String shopName) {
        List<CartItem> items = load(c);
        boolean found = false;
        for (CartItem item : items) {
            if (item.getDishId() == dish.getDishId()) {
                item.setQuantity(item.getQuantity() + 1); // 同菜 +1
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(new CartItem(dish.getDishId(), dish.getShopId(), shopName,
                    dish.getName(), dish.getType(), dish.getPrice(), 1, dish.isCanTakeout()));
        }
        save(c, items);
    }

    /** 增减数量：delta 传 +1 / -1；数量最少到 1，防止出现 0 份 */
    public static void changeQuantity(Context c, int dishId, int delta) {
        List<CartItem> items = load(c);
        for (CartItem item : items) {
            if (item.getDishId() == dishId) {
                int q = item.getQuantity() + delta;
                item.setQuantity(Math.max(1, q));
                break;
            }
        }
        save(c, items);
    }

    /** 删除某行菜品 */
    public static void remove(Context c, int dishId) {
        List<CartItem> items = load(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getDishId() == dishId) {
                items.remove(i);
                break;
            }
        }
        save(c, items);
    }

    /** 当前购物车所属的食堂 id：空车返回 -1（用于"一单一个食堂"的校验） */
    public static int getCartShopId(Context c) {
        List<CartItem> items = load(c);
        return items.isEmpty() ? -1 : items.get(0).getShopId();
    }

    /** 清空购物车（换食堂时用） */
    public static void clear(Context c) {
        save(c, new ArrayList<>());
    }

    // ---------- 计算 / 反浪费 ----------

    /** 算总价：所有行 单价×数量 求和 */
    public static double calcTotal(List<CartItem> items) {
        double total = 0;
        for (CartItem item : items) {
            total += item.getPrice() * item.getQuantity();
        }
        return total;
    }

    /** 反浪费阈值：一单总份数超过它，就认为一个人大概率吃不完。
     *  原来只统计"主食"，轻食、菜点再多也不提醒；现在按整单份数算，范围才对。 */
    public static final int WASTE_QTY_LIMIT = 4;

    /** 统计整单总份数（不分主食/菜/轻食/饮品，都是"点了多少份"） */
    public static int countTotalQty(List<CartItem> items) {
        int count = 0;
        for (CartItem item : items) {
            count += item.getQuantity();
        }
        return count;
    }

    /** 要不要弹反浪费提醒：整单份数超过阈值 */
    public static boolean shouldRemindWaste(List<CartItem> items) {
        return countTotalQty(items) > WASTE_QTY_LIMIT;
    }

    /** 反浪费动作：把整单每道菜的份数都改成 1（"改为单份"） */
    public static void reduceAllToOne(Context c, List<CartItem> items) {
        for (CartItem item : items) {
            item.setQuantity(1);
        }
        save(c, items); // 改完立刻写回本子
    }

    /** 小仓库句柄 */
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
