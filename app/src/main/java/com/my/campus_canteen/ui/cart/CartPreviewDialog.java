package com.my.campus_canteen.ui.cart;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.CartAdapter;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.ui.order.CheckoutActivity;

import java.util.ArrayList;
import java.util.List;

public class CartPreviewDialog {

    /** 面板里改动了购物车后回调，让宿主页面的悬浮条同步刷新 */
    public interface OnChanged {
        void onChanged();
    }

    private CartPreviewDialog() {
        // 工具类：只允许静态调用
    }

    /** 弹出可增减的购物车面板；空车直接不弹 */
    public static void show(Activity activity, OnChanged onChanged) {
        if (CartStore.load(activity).isEmpty()) return;

        new Sheet(activity, onChanged).open();
    }

    /** 一次面板的运行时状态：弹窗 + 适配器 + 合计控件 */
    private static class Sheet {
        private final Activity activity;
        private final OnChanged onChanged;
        private final BottomSheetDialog dialog;
        private final CartAdapter adapter;
        private final TextView tvCount;
        private final TextView tvTotal;
        private final View content;

        Sheet(Activity activity, OnChanged onChanged) {
            this.activity = activity;
            this.onChanged = onChanged;
            this.dialog = new BottomSheetDialog(activity);
            this.content = LayoutInflater.from(activity)
                    .inflate(R.layout.dialog_cart_sheet, null);
            this.tvCount = content.findViewById(R.id.tv_sheet_count);
            this.tvTotal = content.findViewById(R.id.tv_sheet_total);

            RecyclerView rv = content.findViewById(R.id.rv_cart_sheet);
            rv.setLayoutManager(new LinearLayoutManager(activity));

            // 加减删都直接写进购物车本子，然后整表刷新
            this.adapter = new CartAdapter(new CartAdapter.OnCartActionListener() {
                @Override
                public void onPlus(CartItem item) {
                    CartStore.changeQuantity(activity, item.getDishId(), 1);
                    refresh();
                }

                @Override
                public void onMinus(CartItem item) {
                    if (item.getQuantity() <= 1) {
                        CartStore.remove(activity, item.getDishId()); // 1 份再减 = 删掉
                    } else {
                        CartStore.changeQuantity(activity, item.getDishId(), -1);
                    }
                    refresh();
                }

                @Override
                public void onDelete(CartItem item) {
                    CartStore.remove(activity, item.getDishId());
                    refresh();
                }
            });
            rv.setAdapter(adapter);

            content.findViewById(R.id.btn_sheet_checkout).setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, CheckoutActivity.class));
            });

            // 清空购物车：先二次确认，确认后清空并收起面板
            content.findViewById(R.id.btn_sheet_clear).setOnClickListener(v -> confirmClear());

            // 面板收起时也同步一次，防止宿主浮条停留在旧金额
            dialog.setOnDismissListener(d -> notifyHost());
        }

        void open() {
            refresh(); // 先把数据填进去，再显示
            dialog.setContentView(content);
            dialog.show();
        }

        /** 清空购物车：和购物车页一致的二次确认，防止误点 */
        private void confirmClear() {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.cart_clear)
                    .setMessage(R.string.cart_clear_msg)
                    .setPositiveButton(R.string.cart_clear_ok, (d, which) -> {
                        CartStore.clear(activity);
                        Toast.makeText(activity, R.string.toast_cart_cleared,
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        notifyHost();
                    })
                    .setNegativeButton(R.string.cart_clear_cancel, null)
                    .show();
        }

        /** 重新读购物车 → 填列表 → 刷新件数与合计；空车则收起面板 */
        private void refresh() {
            List<CartItem> items = CartStore.load(activity);
            if (items.isEmpty()) {
                dialog.dismiss();
                notifyHost();
                return;
            }

            int count = 0;
            for (CartItem item : items) {
                count += item.getQuantity();
            }
            tvCount.setText(activity.getString(R.string.cart_item_count, count));
            tvTotal.setText(activity.getString(R.string.cart_total, CartStore.calcTotal(items)));

            adapter.replaceData(new ArrayList<Object>(items));
            notifyHost();
        }

        private void notifyHost() {
            if (onChanged != null) onChanged.onChanged();
        }
    }
}
