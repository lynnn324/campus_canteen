package com.my.campus_canteen.ui.mine;

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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.AuthStore;
import com.my.campus_canteen.data.FavoritesStore;
import com.my.campus_canteen.data.OrderStore;
import com.my.campus_canteen.model.Order;
import com.my.campus_canteen.model.OrderStatus;

import java.util.List;
import java.util.Locale;

public class MineFragment extends Fragment {

    private TextView tvUserName;
    private TextView tvUserSignature;
    private TextView tvStatOrder;
    private TextView tvStatFav;
    private TextView tvStatSpend;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载"我的"页布局
        return inflater.inflate(R.layout.fragment_mine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvUserName = view.findViewById(R.id.tv_mine_name);
        tvUserSignature = view.findViewById(R.id.tv_mine_signature);
        tvStatOrder = view.findViewById(R.id.tv_stat_order);
        tvStatFav = view.findViewById(R.id.tv_stat_fav);
        tvStatSpend = view.findViewById(R.id.tv_stat_spend);

        // 用户卡：未登录点击去登录；已登录点击可退出
        view.findViewById(R.id.row_mine_user).setOnClickListener(v -> onUserCardClick());

        // 我的订单：直接切到底部导航的"订单"Tab（复用已有页面，不重复造）
        view.findViewById(R.id.row_my_order).setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_order); // 交给底部导航监听器完成切换
        });

        // 我的收藏：进入收藏列表页
        view.findViewById(R.id.row_my_fav).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoritesActivity.class)));

        // 设置：进入设置页
        view.findViewById(R.id.row_setting).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccount(); // 登录/退出后回来，昵称要跟着变
        refreshStats();   // 从收藏页/设置页回来，数字要重算
    }

    /** 用户卡：登录了就显示用户名，没登录就提示去登录 */
    private void refreshAccount() {
        if (AuthStore.isLoggedIn(requireContext())) {
            tvUserName.setText(AuthStore.username(requireContext()));
            tvUserSignature.setText(R.string.mine_logged_signature);
        } else {
            tvUserName.setText(R.string.mine_login_name);
            tvUserSignature.setText(R.string.mine_login_signature);
        }
    }

    /** 点用户卡：已登录弹退出确认，未登录进登录页 */
    private void onUserCardClick() {
        if (AuthStore.isLoggedIn(requireContext())) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.auth_logout)
                    .setMessage(R.string.auth_logout_msg)
                    .setPositiveButton(R.string.auth_logout_ok, (dialog, which) -> {
                        AuthStore.logout(requireContext());
                        refreshAccount();
                        Toast.makeText(requireContext(), R.string.auth_logged_out,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.auth_logout_cancel, null)
                    .show();
        } else {
            startActivity(new Intent(requireContext(), LoginActivity.class));
        }
    }

    /** 数据概览：订单数、收藏数、累计消费（不含已取消订单） */
    private void refreshStats() {
        List<Order> orders = OrderStore.loadAll(requireContext());

        double spend = 0;
        for (Order order : orders) {
            // 取消的订单不算花出去的钱
            if (order.getStatus() != OrderStatus.CANCELED) {
                spend += order.getTotal();
            }
        }

        tvStatOrder.setText(String.valueOf(orders.size()));
        tvStatFav.setText(String.valueOf(FavoritesStore.count(requireContext())));
        tvStatSpend.setText(getString(R.string.mine_spend_value,
                String.format(Locale.CHINA, "%.1f", spend)));
    }
}
