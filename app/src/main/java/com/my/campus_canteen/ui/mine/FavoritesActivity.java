package com.my.campus_canteen.ui.mine;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.FavoritesAdapter;
import com.my.campus_canteen.data.FavoritesStore;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.model.Shop;
import com.my.campus_canteen.ui.shop.StallDetailActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FavoritesActivity extends AppCompatActivity {

    private final List<Dish> favDishes = new ArrayList<>();
    private final Map<Integer, String> shopNameMap = new HashMap<>(); // 食堂编号→食堂名
    private FavoritesAdapter adapter;
    private RecyclerView rvFav;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // 返回箭头
        findViewById(R.id.btn_fav_back).setOnClickListener(v -> finish());

        // 建立"食堂编号 → 食堂名"对照表（显示来源用）
        for (Shop shop : JsonParser.parseShops(this)) {
            shopNameMap.put(shop.getShopId(), shop.getName());
        }

        rvFav = findViewById(R.id.rv_favorites);
        tvEmpty = findViewById(R.id.tv_fav_empty);
        rvFav.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FavoritesAdapter(favDishes, shopNameMap, new FavoritesAdapter.OnFavActionListener() {
            @Override
            public void onUnfavorite(Dish dish) {
                FavoritesStore.remove(FavoritesActivity.this, dish.getDishId());
                Toast.makeText(FavoritesActivity.this, R.string.fav_removed, Toast.LENGTH_SHORT).show();
                reload(); // 取消后立刻从列表里去掉
            }

            @Override
            public void onOpenShop(Dish dish) {
                // 跳到该菜所属档口详情页（在那里可加购）
                Intent intent = new Intent(FavoritesActivity.this, StallDetailActivity.class);
                intent.putExtra("shop_id", dish.getShopId());
                intent.putExtra("shop_name", shopNameMap.get(dish.getShopId()));
                startActivity(intent);
            }
        });
        rvFav.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // 回到页面时重读收藏（可能在别处改了收藏状态）
    }

    /** 重读收藏：按收藏顺序从 dishes.json 里找回菜品，刷新列表与空态 */
    private void reload() {
        favDishes.clear();

        List<Integer> ids = FavoritesStore.getAll(this);
        // 把菜品按 dishId 建索引，方便一次次查
        Map<Integer, Dish> dishById = new HashMap<>();
        for (Dish dish : JsonParser.parseDishes(this)) {
            dishById.put(dish.getDishId(), dish);
        }
        for (Integer id : ids) {
            Dish dish = dishById.get(id);
            if (dish != null) {
                favDishes.add(dish); // 数据里已被删除的菜直接跳过
            }
        }

        adapter.notifyDataSetChanged();

        boolean empty = favDishes.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvFav.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
