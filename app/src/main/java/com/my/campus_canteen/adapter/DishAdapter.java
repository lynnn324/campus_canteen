package com.my.campus_canteen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.FavoritesStore;
import com.my.campus_canteen.model.Dish;

import java.util.List;
import java.util.Map;

public class DishAdapter extends RecyclerView.Adapter<DishAdapter.DishHolder> {

    /** 加购回调：点击"加购"时通知外部处理 */
    public interface OnDishAddListener {
        void onAdd(Dish dish);
    }

    /** 整行点击回调：点某道菜进入菜品详情页（可以为空，表示不支持点击） */
    public interface OnDishClickListener {
        void onDishClick(Dish dish);
    }

    private final List<Dish> dishes;
    private final OnDishAddListener listener;           // 加购点击回调
    private final Map<Integer, String> shopNameMap;     // 食堂编号→食堂名；为 null 则不显示"来源"
    private final OnDishClickListener clickListener;    // 整行点击回调；为 null 则整行不可点

    /** 档口详情页用：不显示"来源"，整行不可点 */
    public DishAdapter(List<Dish> dishes, OnDishAddListener listener) {
        this(dishes, listener, null, null);
    }

    /** 分类页用：传 shopNameMap 后，每行会显示"来自 XX食堂"，整行不可点 */
    public DishAdapter(List<Dish> dishes, OnDishAddListener listener,
                       @Nullable Map<Integer, String> shopNameMap) {
        this(dishes, listener, shopNameMap, null);
    }

    /** 完整版：既能显示来源，也能响应整行点击（进入菜品详情页） */
    public DishAdapter(List<Dish> dishes, OnDishAddListener listener,
                       @Nullable Map<Integer, String> shopNameMap,
                       @Nullable OnDishClickListener clickListener) {
        this.dishes = dishes;
        this.listener = listener;
        this.shopNameMap = shopNameMap;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public DishHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dish, parent, false);
        return new DishHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DishHolder holder, int position) {
        Dish dish = dishes.get(position);

        holder.tvName.setText(dish.getName());
        holder.tvPrice.setText("¥" + dish.getPrice());
        holder.tvType.setText(dish.getType());

        bindImage(holder, dish);

        // 趣味标签：有 label 才亮出来，没有就藏起来，不影响布局
        String label = dish.getLabel();
        if (label == null || label.isEmpty()) {
            holder.tvLabel.setVisibility(View.GONE);
        } else {
            holder.tvLabel.setVisibility(View.VISIBLE);
            holder.tvLabel.setText(label);
        }

        // 来源食堂：只有分类页（传了 shopNameMap）才显示
        bindSource(holder, dish);

        // 用角标文字表达支持方式：都支持→"外卖 堂食"；只支持外卖→"外卖"；只堂食→"堂食"
        StringBuilder mode = new StringBuilder();
        if (dish.isCanTakeout()) mode.append("外卖 ");
        if (dish.isCanDineIn()) mode.append("堂食");
        int color = mode.toString().trim().isEmpty()
                ? MaterialColors.getColor(holder.itemView, android.R.attr.textColorSecondary)
                : MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary);
        holder.tvMode.setText(mode.toString().trim());
        holder.tvMode.setBackgroundColor(color);

        // 加购按钮：点了就回调外部去执行"加入购物车"逻辑
        holder.btnAdd.setOnClickListener(v -> listener.onAdd(dish));

        // 整行点击：进入菜品详情页（未传回调时置空，避免误触）
        if (clickListener == null) {
            holder.itemView.setOnClickListener(null);
        } else {
            holder.itemView.setOnClickListener(v -> clickListener.onDishClick(dish));
        }

        // 收藏爱心：显示当前收藏状态，点了就切换（数据写在 FavoritesStore，下次进来还在）
        bindFavorite(holder, dish);
    }

    /** 绑定收藏爱心：已收藏=实心+主色，未收藏=空心+次要色；点击即切换并刷新图标 */
    private void bindFavorite(DishHolder holder, Dish dish) {
        Context ctx = holder.itemView.getContext();
        refreshFavIcon(holder.btnFav, FavoritesStore.isFavorite(ctx, dish.getDishId()));
        holder.btnFav.setOnClickListener(v -> {
            boolean nowFav = FavoritesStore.toggle(ctx, dish.getDishId());
            refreshFavIcon(holder.btnFav, nowFav);
            Toast.makeText(ctx, nowFav ? R.string.fav_added : R.string.fav_removed,
                    Toast.LENGTH_SHORT).show();
        });
    }

    /** 按收藏状态刷新爱心图标与颜色 */
    private void refreshFavIcon(ImageView fav, boolean on) {
        fav.setImageResource(on ? R.drawable.ic_fav_on : R.drawable.ic_fav_off);
        int color = on
                ? MaterialColors.getColor(fav, com.google.android.material.R.attr.colorSecondary)
                : MaterialColors.getColor(fav, android.R.attr.textColorSecondary);
        fav.setColorFilter(color);
    }

    /** 绑定菜品配图：数据里存的是 drawable 资源名（如 "dish_steamed_bun"），按名字查成资源 id */
    private void bindImage(DishHolder holder, Dish dish) {
        int resId = R.drawable.ic_meal; // 兜底：查不到就用通用图标，保证不崩
        String imgName = dish.getImage();
        if (imgName != null && !imgName.isEmpty()) {
            int id = holder.itemView.getResources().getIdentifier(
                    imgName, "drawable", holder.itemView.getContext().getPackageName());
            if (id != 0) {
                resId = id;
            }
        }
        holder.imgDish.setImageResource(resId);
    }

    /** 绑定来源食堂：shopNameMap 为空（档口详情页）或查不到名字时，隐藏这一行 */
    private void bindSource(DishHolder holder, Dish dish) {
        if (shopNameMap == null) {
            holder.tvSource.setVisibility(View.GONE);
            return;
        }
        String shopName = shopNameMap.get(dish.getShopId());
        if (shopName == null || shopName.isEmpty()) {
            holder.tvSource.setVisibility(View.GONE);
        } else {
            holder.tvSource.setVisibility(View.VISIBLE);
            holder.tvSource.setText(holder.itemView.getContext()
                    .getString(R.string.dish_from_shop, shopName));
        }
    }

    @Override
    public int getItemCount() {
        return dishes.size();
    }

    // ViewHolder：缓存一行内的控件引用
    static class DishHolder extends RecyclerView.ViewHolder {
        ImageView imgDish;
        TextView tvName;
        TextView tvLabel;
        TextView tvType;
        TextView tvSource;
        TextView tvPrice;
        TextView tvMode;
        TextView btnAdd;
        ImageView btnFav;

        DishHolder(@NonNull View itemView) {
            super(itemView);
            imgDish = itemView.findViewById(R.id.img_dish);
            tvName = itemView.findViewById(R.id.tv_dish_name);
            tvLabel = itemView.findViewById(R.id.tv_dish_label);
            tvType = itemView.findViewById(R.id.tv_dish_type);
            tvSource = itemView.findViewById(R.id.tv_dish_source);
            tvPrice = itemView.findViewById(R.id.tv_dish_price);
            tvMode = itemView.findViewById(R.id.tv_dish_mode);
            btnAdd = itemView.findViewById(R.id.btn_add_dish);
            btnFav = itemView.findViewById(R.id.btn_fav_dish);
        }
    }
}
