package com.my.campus_canteen.ui.cart;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.my.campus_canteen.R;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.model.Shop;

import java.util.List;

/**
 * 加购公共流程：外卖校验、一单一个食堂校验、写入购物车、反浪费提醒。
 * 档口详情、分类页、菜品详情三个页面共用，避免同一套规则各写一遍。
 */
public final class CartActions {

    /** 购物车发生变化（已写入，或反浪费改成单份后） */
    public interface OnCartChanged {
        void onCartChanged(List<CartItem> items);
    }

    private CartActions() {
    }

    /**
     * 加购：quantity 为份数，菜品详情页可一次加多份。
     * 校验不通过时直接提示并返回，不写购物车。
     */
    public static void add(Context context, Dish dish, String shopName,
                           int quantity, OnCartChanged callback) {
        if (CartStore.isTakeout(context) && !dish.isCanTakeout()) {
            Toast.makeText(context, R.string.toast_not_support_takeout, Toast.LENGTH_SHORT).show();
            return;
        }

        int cartShopId = CartStore.getCartShopId(context);
        if (cartShopId != -1 && cartShopId != dish.getShopId()) {
            confirmSwitchShop(context, cartShopId, dish, shopName, quantity, callback);
            return;
        }

        doAdd(context, dish, shopName, quantity, callback);
    }

    /** 换食堂弹窗：确认后清空旧单，再加入新食堂这道菜 */
    private static void confirmSwitchShop(Context context, int cartShopId, Dish dish,
                                          String shopName, int quantity, OnCartChanged callback) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.cart_diff_shop_title)
                .setMessage(context.getString(R.string.cart_diff_shop_msg,
                        shopNameOf(context, cartShopId), shopName))
                .setPositiveButton(R.string.cart_diff_shop_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CartStore.clear(context);
                        doAdd(context, dish, shopName, quantity, callback);
                    }
                })
                .setNegativeButton(R.string.cart_diff_shop_cancel, null)
                .show();
    }

    /** 按份数写入购物车，再检查是否要弹反浪费提醒 */
    private static void doAdd(Context context, Dish dish, String shopName,
                              int quantity, OnCartChanged callback) {
        for (int i = 0; i < quantity; i++) {
            CartStore.add(context, dish, shopName);
        }

        List<CartItem> items = CartStore.load(context);
        if (callback != null) callback.onCartChanged(items);

        if (CartStore.shouldRemindWaste(items)) {
            showWasteReminder(context, CartStore.countTotalQty(items), callback);
        }

        Toast.makeText(context, R.string.toast_added, Toast.LENGTH_SHORT).show();
    }

    /** 反浪费弹窗：确认后整单每道菜改成 1 份，并通知调用方刷新 */
    public static void showWasteReminder(Context context, int totalQty, OnCartChanged callback) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_waste_title)
                .setMessage(context.getString(R.string.dialog_waste_msg, totalQty))
                .setPositiveButton(R.string.dialog_waste_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CartStore.reduceAllToOne(context, CartStore.load(context));
                        if (callback != null) callback.onCartChanged(CartStore.load(context));
                    }
                })
                .setNegativeButton(R.string.dialog_waste_cancel, null)
                .show();
    }

    /** 按档口编号取档口名（换食堂弹窗、补全购物车分组标题用），查不到返回空串 */
    public static String shopNameOf(Context context, int shopId) {
        for (Shop shop : JsonParser.parseShops(context)) {
            if (shop.getShopId() == shopId) {
                return shop.getName();
            }
        }
        return "";
    }
}
