package com.my.campus_canteen.ui.cart;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.CartAdapter;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.ui.order.CheckoutActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartFragment extends Fragment {

    private RecyclerView rvCart;
    private TextView tvCartEmpty;
    private TextView tvCartTotal;
    private TextView btnCheckout;
    private TextView btnClearCart; // 顶部"清空"按钮
    private View llCartBottom;
    private CartAdapter cartAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvCart = view.findViewById(R.id.rv_cart);
        tvCartEmpty = view.findViewById(R.id.tv_cart_empty);
        tvCartTotal = view.findViewById(R.id.tv_cart_total);
        btnCheckout = view.findViewById(R.id.btn_checkout);
        btnClearCart = view.findViewById(R.id.btn_clear_cart);
        llCartBottom = view.findViewById(R.id.ll_cart_bottom);

        rvCart.setLayoutManager(new LinearLayoutManager(getContext()));
        // 传入操作回调：加/减/删除 都重新读取数据、刷新界面
        cartAdapter = new CartAdapter(new CartAdapter.OnCartActionListener() {
            @Override
            public void onPlus(CartItem item) {
                CartStore.changeQuantity(requireContext(), item.getDishId(), +1);
                // 反浪费：整单总份数超过阈值时弹窗问是否改为单份（不再只看主食）
                List<CartItem> after = CartStore.load(requireContext());
                if (CartStore.shouldRemindWaste(after)) {
                    CartActions.showWasteReminder(requireContext(),
                            CartStore.countTotalQty(after), items -> refresh());
                }
                refresh();
            }

            @Override
            public void onMinus(CartItem item) {
                CartStore.changeQuantity(requireContext(), item.getDishId(), -1);
                refresh();
            }

            @Override
            public void onDelete(CartItem item) {
                CartStore.remove(requireContext(), item.getDishId());
                refresh();
            }
        });
        rvCart.setAdapter(cartAdapter);

        // 结算按钮：达标才可结算；未达标点一下给个提示，别让用户以为按钮坏了
        btnCheckout.setOnClickListener(v -> {
            double total = CartStore.calcTotal(CartStore.load(requireContext()));
            int minPrice = CartStore.getMinPrice(requireContext());
            if (total >= minPrice) {
                // 达标：跳转到确认订单页（Phase 5）
                startActivity(new Intent(requireContext(), CheckoutActivity.class));
            } else {
                // 未达标：提示还差多少钱（复用底部那行同一句文案）
                Toast.makeText(requireContext(), getString(R.string.cart_min_short, minPrice - total),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 清空购物车：二次确认后清零并刷新（放在购物车页顶部右侧）
        btnClearCart.setOnClickListener(v -> confirmClearCart());

        refresh();
    }

    /** 清空购物车确认弹窗：避免误触把整单点菜清掉 */
    private void confirmClearCart() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.cart_clear)
                .setMessage(R.string.cart_clear_msg)
                .setPositiveButton(R.string.cart_clear_ok, (dialog, which) -> {
                    CartStore.clear(requireContext());
                    refresh();
                    Toast.makeText(requireContext(), R.string.toast_cart_cleared,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cart_clear_cancel, null)
                .show();
    }

    /**
     * 整体刷新：读取购物车 → 按档口分组 → 传给适配器 → 更新总价与结算按钮。
     */
    private void refresh() {
        List<CartItem> items = CartStore.load(requireContext());
        List<Object> rows = new ArrayList<>();

        // 按档口分组（LinkedHashMap 保持插入顺序：先点的档口排前面）
        Map<Integer, List<CartItem>> groups = new LinkedHashMap<>();
        for (CartItem item : items) {
            groups.computeIfAbsent(item.getShopId(), k -> new ArrayList<>()).add(item);
        }
        // 每组先放一个"档口名"标题，再放这组的所有商品
        for (Map.Entry<Integer, List<CartItem>> entry : groups.entrySet()) {
            rows.add(entry.getValue().get(0).getShopName()); // 标题行
            rows.addAll(entry.getValue());                   // 商品行
        }

        cartAdapter.replaceData(rows);

        // 空态：没有商品就显示提示，隐藏列表和结算栏
        boolean empty = items.isEmpty();
        tvCartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        llCartBottom.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnClearCart.setVisibility(empty ? View.GONE : View.VISIBLE); // 空车没有可清的东西

        updateBottomBar(items);
    }

    /**
     * 更新底部：总价 + 起送价规则。
     * 未达标（仅外卖时才有起送价）：按钮置灰，文案"还差 X 元起送"；
     * 达标：按钮变主色，文案"去结算 ¥总价"。
     */
    private void updateBottomBar(List<CartItem> items) {
        double total = CartStore.calcTotal(items);
        int minPrice = CartStore.getMinPrice(requireContext());

        tvCartTotal.setText(getString(R.string.cart_total, total));

        if (total >= minPrice) {
            // 达标：可结算（主色从主题取，护眼主题下自动变绿）
            btnCheckout.setEnabled(true);
            btnCheckout.setBackgroundColor(MaterialColors.getColor(btnCheckout, com.google.android.material.R.attr.colorPrimary));
            btnCheckout.setText(getString(R.string.cart_checkout_go, total));
        } else {
            // 未达标：置灰，提示还差多少钱
            btnCheckout.setEnabled(false);
            btnCheckout.setBackgroundColor(MaterialColors.getColor(btnCheckout, android.R.attr.textColorSecondary));
            btnCheckout.setText(getString(R.string.cart_min_short, minPrice - total));
        }
    }
}
