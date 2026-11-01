package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Order;
import com.my.campus_canteen.model.OrderStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderStore {

    private static final String PREFS = "order_prefs";
    private static final String KEY_ORDERS = "orders";

    private OrderStore() {
        // 工具类：私有构造，只允许静态调用
    }

    /** 读全部订单（已按下单时间倒序：最新的在最上面） */
    public static List<Order> loadAll(Context c) {
        List<Order> list = new ArrayList<>();
        String json = sp(c).getString(KEY_ORDERS, "");
        if (json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(parseOrder(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 倒序：orderTime 大的（新订单）排前面
        Collections.sort(list, (a, b) -> Long.compare(b.getOrderTime(), a.getOrderTime()));
        return list;
    }

    /** 保存一笔新订单（追加到最前面） */
    public static void saveOrder(Context c, Order order) {
        List<Order> list = loadAll(c);
        list.add(order);
        writeAll(c, list);
    }

    /** 取消订单：只允许待出餐状态（取消 = 状态变成已取消，不是删掉） */
    public static void cancelOrder(Context c, long orderId) {
        List<Order> list = loadAll(c);
        for (Order order : list) {
            if (order.getOrderId() == orderId && order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELED);
                break; // 找到并改完就退出，不再继续找
            }
        }
        writeAll(c, list); // 把改动后的整个列表写回
    }

    /** 推进订单到下一状态（模拟状态流转） */
    public static void updateStatus(Context c, long orderId, OrderStatus status) {
        List<Order> list = loadAll(c);
        for (Order order : list) {
            if (order.getOrderId() == orderId) {
                order.setStatus(status);
            }
        }
        writeAll(c, list);
    }

    /** 按订单号找一笔订单（找不到返回 null） */
    public static Order findById(Context c, long orderId) {
        for (Order order : loadAll(c)) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        return null;
    }

    // ---------- 序列化：Order <-> JSON ----------

    private static Order parseOrder(JSONObject o) throws Exception {
        JSONArray itemArr = o.getJSONArray("items");
        List<CartItem> items = new ArrayList<>();
        for (int i = 0; i < itemArr.length(); i++) {
            JSONObject io = itemArr.getJSONObject(i);
            items.add(new CartItem(
                    io.getInt("dishId"),
                    io.getInt("shopId"),
                    io.getString("shopName"),
                    io.getString("name"),
                    io.getString("type"),
                    io.getDouble("price"),
                    io.getInt("quantity"),
                    io.getBoolean("canTakeout")
            ));
        }
        return new Order(
                o.getLong("orderId"),
                items,
                o.getDouble("total"),
                o.getString("mode"),
                OrderStatus.valueOf(o.getString("status")),
                o.getInt("deliveryFee"),
                o.getInt("estimatedMinutes"),
                o.getLong("orderTime"),
                o.has("pickTime") ? o.getString("pickTime") : "",
                // 送达位置是后加字段：老订单没有就当空串，兼容不崩
                o.has("deliverySpot") ? o.getString("deliverySpot") : ""
        );
    }

    private static void writeAll(Context c, List<Order> orders) {
        JSONArray arr = new JSONArray();
        for (Order order : orders) {
            try {
                JSONObject o = new JSONObject();
                o.put("orderId", order.getOrderId());
                o.put("mode", order.getMode());
                o.put("status", order.getStatus().name());
                o.put("total", order.getTotal());
                o.put("deliveryFee", order.getDeliveryFee());
                o.put("estimatedMinutes", order.getEstimatedMinutes());
                o.put("orderTime", order.getOrderTime());
                o.put("pickTime", order.getPickTime());
                o.put("deliverySpot", order.getDeliverySpot());

                JSONArray itemArr = new JSONArray();
                for (CartItem item : order.getItems()) {
                    JSONObject io = new JSONObject();
                    io.put("dishId", item.getDishId());
                    io.put("shopId", item.getShopId());
                    io.put("shopName", item.getShopName());
                    io.put("name", item.getName());
                    io.put("type", item.getType());
                    io.put("price", item.getPrice());
                    io.put("quantity", item.getQuantity());
                    io.put("canTakeout", item.isCanTakeout());
                    itemArr.put(io);
                }
                o.put("items", itemArr);
                arr.put(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sp(c).edit().putString(KEY_ORDERS, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
