package com.my.campus_canteen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.model.CartItem;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0; // 分组标题
    public static final int TYPE_ITEM = 1;   // 商品行

    /** 操作回调：加/减/删除（由 CartFragment 决定怎么改数据） */
    public interface OnCartActionListener {
        void onPlus(CartItem item);
        void onMinus(CartItem item);
        void onDelete(CartItem item);
    }

    // 行数据：Object 列表，String 表示分组标题，CartItem 表示商品
    private final List<Object> rows = new ArrayList<>();
    private final OnCartActionListener listener;

    public CartAdapter(OnCartActionListener listener) {
        this.listener = listener;
    }

    /** 整体替换列表内容（每次数据变了都重新分组传入） */
    public void replaceData(List<Object> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        // String 是标题行，CartItem 是商品行
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_cart_header, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_cart, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            // 标题行：显示档口名
            ((HeaderHolder) holder).tvShopName.setText((String) rows.get(position));
        } else {
            // 商品行：绑定数据 + 按钮点击
            CartItem item = (CartItem) rows.get(position);
            ItemHolder itemHolder = (ItemHolder) holder;
            itemHolder.tvName.setText(item.getName());
            itemHolder.tvPrice.setText("¥" + item.getPrice());
            itemHolder.tvQty.setText(String.valueOf(item.getQuantity()));

            // 数量已经是 1 时，"减"按钮置灰不可点（不能减到 0）
            itemHolder.btnMinus.setEnabled(item.getQuantity() > 1);

            itemHolder.btnPlus.setOnClickListener(v -> listener.onPlus(item));
            itemHolder.btnMinus.setOnClickListener(v -> listener.onMinus(item));
            itemHolder.btnDelete.setOnClickListener(v -> listener.onDelete(item));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    // 标题行 ViewHolder
    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView tvShopName;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            tvShopName = itemView.findViewById(R.id.tv_group_shop_name);
        }
    }

    // 商品行 ViewHolder
    static class ItemHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvPrice;
        TextView tvQty;
        TextView btnPlus;
        TextView btnMinus;
        TextView btnDelete;

        ItemHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_cart_name);
            tvPrice = itemView.findViewById(R.id.tv_cart_price);
            tvQty = itemView.findViewById(R.id.tv_cart_qty);
            btnPlus = itemView.findViewById(R.id.btn_cart_plus);
            btnMinus = itemView.findViewById(R.id.btn_cart_minus);
            btnDelete = itemView.findViewById(R.id.btn_cart_delete);
        }
    }
}
