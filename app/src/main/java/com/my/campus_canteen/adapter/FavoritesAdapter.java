package com.my.campus_canteen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.model.Dish;

import java.util.List;
import java.util.Map;

public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.FavHolder> {

    /** 操作回调：取消收藏 / 打开所属档口 */
    public interface OnFavActionListener {
        void onUnfavorite(Dish dish);
        void onOpenShop(Dish dish);
    }

    private final List<Dish> dishes;
    private final Map<Integer, String> shopNameMap;     // 食堂编号→食堂名（显示"来自 XX"）
    private final OnFavActionListener listener;

    public FavoritesAdapter(List<Dish> dishes, Map<Integer, String> shopNameMap,
                            OnFavActionListener listener) {
        this.dishes = dishes;
        this.shopNameMap = shopNameMap;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FavHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fav, parent, false);
        return new FavHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavHolder holder, int position) {
        Dish dish = dishes.get(position);

        holder.tvName.setText(dish.getName());
        holder.tvPrice.setText("¥" + dish.getPrice());

        // 来源食堂：查表显示"来自 XX食堂"
        String shopName = shopNameMap.get(dish.getShopId());
        holder.tvSource.setText(holder.itemView.getContext()
                .getString(R.string.dish_from_shop, shopName == null ? "" : shopName));

        bindImage(holder, dish);

        // 整行点击：去该菜所属档口详情页
        holder.itemView.setOnClickListener(v -> listener.onOpenShop(dish));
        // 爱心：取消收藏
        holder.btnUnfav.setOnClickListener(v -> listener.onUnfavorite(dish));
    }

    /** 绑定配图：数据里存 drawable 资源名，按名查 id，查不到用通用图标兜底 */
    private void bindImage(FavHolder holder, Dish dish) {
        int resId = R.drawable.ic_meal;
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

    @Override
    public int getItemCount() {
        return dishes.size();
    }

    // ViewHolder：缓存一行内的控件引用
    static class FavHolder extends RecyclerView.ViewHolder {
        ImageView imgDish;
        TextView tvName;
        TextView tvSource;
        TextView tvPrice;
        ImageView btnUnfav;

        FavHolder(@NonNull View itemView) {
            super(itemView);
            imgDish = itemView.findViewById(R.id.img_fav_dish);
            tvName = itemView.findViewById(R.id.tv_fav_name);
            tvSource = itemView.findViewById(R.id.tv_fav_source);
            tvPrice = itemView.findViewById(R.id.tv_fav_price);
            btnUnfav = itemView.findViewById(R.id.btn_unfav);
        }
    }
}
