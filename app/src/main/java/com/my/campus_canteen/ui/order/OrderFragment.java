package com.my.campus_canteen.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.OrderAdapter;
import com.my.campus_canteen.data.OrderStore;
import com.my.campus_canteen.model.Order;

import java.util.List;

public class OrderFragment extends Fragment {

    private RecyclerView rvOrders;
    private TextView tvEmpty;
    private OrderAdapter orderAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvOrders = view.findViewById(R.id.rv_orders);
        tvEmpty = view.findViewById(R.id.tv_order_empty);

        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));

        // 卡片操作回调：取消 / 进详情
        orderAdapter = new OrderAdapter(new OrderAdapter.OnOrderActionListener() {
            @Override
            public void onCancel(Order order) {
                showCancelDialog(order);
            }

            @Override
            public void onOpen(Order order) {
                Intent intent = new Intent(requireContext(), OrderDetailActivity.class);
                intent.putExtra("order_id", order.getOrderId());
                startActivity(intent);
            }
        });
        rvOrders.setAdapter(orderAdapter);
    }

    /** 回到本页（含从详情页返回）时刷新，保证显示最新订单 */
    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /** 读取历史订单（倒序）→ 刷列表 → 切换空态 */
    private void refresh() {
        List<Order> orders = OrderStore.loadAll(requireContext());
        orderAdapter.replaceData(orders);

        boolean empty = orders.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvOrders.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 取消订单确认弹窗：确认后状态变"已取消" */
    private void showCancelDialog(Order order) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(R.string.cancel_confirm_msg)
                .setPositiveButton(R.string.cancel_ok, (dialog, which) -> {
                    OrderStore.cancelOrder(requireContext(), order.getOrderId());
                    refresh();
                })
                .setNegativeButton(R.string.cancel_keep, null)
                .show();
    }
}
