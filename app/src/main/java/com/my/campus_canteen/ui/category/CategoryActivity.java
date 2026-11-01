package com.my.campus_canteen.ui.category;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.my.campus_canteen.R;
import com.my.campus_canteen.adapter.DishAdapter;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.model.Shop;
import com.my.campus_canteen.ui.cart.CartActions;
import com.my.campus_canteen.ui.cart.CartPreviewDialog;
import com.my.campus_canteen.ui.order.CheckoutActivity;
import com.my.campus_canteen.ui.shop.DishDetailActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryActivity extends AppCompatActivity {

    private final Map<Integer, String> shopNameMap = new HashMap<>(); // 食堂编号 → 食堂名（加购写进购物车做分组标题）
    private View cartBar;
    private TextView tvCartTotal;
    private TextView tvCartHint;
    private TextView tvCartCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        // 接收首页传过来的"分类名"（早餐/套餐/轻食/饮品）
        String category = getIntent().getStringExtra("category");
        String title = (category == null || category.isEmpty())
                ? getString(R.string.category_page_default) : category;

        TextView tvTitle = findViewById(R.id.tv_category_title);
        tvTitle.setText(getString(R.string.category_page_title, title));

        // 返回箭头：回到首页
        findViewById(R.id.btn_category_back).setOnClickListener(v -> finish());

        // 建立"食堂编号 → 食堂名"对照表：分类页是跨食堂的，加购时要知道这道菜属于哪家
        for (Shop shop : JsonParser.parseShops(this)) {
            shopNameMap.put(shop.getShopId(), shop.getName());
        }

        // 过滤出该分类下的所有菜品（category 为后加字段，老数据可能为空）
        List<Dish> categoryDishes = new ArrayList<>();
        for (Dish dish : JsonParser.parseDishes(this)) {
            if (category != null && category.equals(dish.getCategory())) {
                categoryDishes.add(dish);
            }
        }

        RecyclerView rvDish = findViewById(R.id.rv_category_dish);
        rvDish.setLayoutManager(new LinearLayoutManager(this));
        rvDish.setAdapter(new DishAdapter(categoryDishes, this::addToCart, shopNameMap, this::openDishDetail));

        // 空态：该分类暂无菜品
        TextView tvEmpty = findViewById(R.id.tv_category_empty);
        if (categoryDishes.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvDish.setVisibility(View.GONE);
        }

        // 美团式悬浮购物车：底部浮条，加购后实时刷新金额与件数
        cartBar = findViewById(R.id.ll_cart_bar);
        tvCartTotal = findViewById(R.id.tv_cart_total);
        tvCartHint = findViewById(R.id.tv_cart_hint);
        tvCartCount = findViewById(R.id.tv_cart_count);
        findViewById(R.id.btn_cart_checkout).setOnClickListener(v ->
                startActivity(new Intent(this, CheckoutActivity.class)));
        // 点浮条本身（"去结算"以外的地方）弹出可增减的购物车面板
        cartBar.setOnClickListener(v -> CartPreviewDialog.show(this, this::refreshCartBar));
        refreshCartBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCartBar(); // 从购物车/结算页回来时同步最新件数与金额
    }

    /** 点某道菜：进入菜品详情页，带上菜品编号与所属档口名 */
    private void openDishDetail(Dish dish) {
        Intent intent = new Intent(this, DishDetailActivity.class);
        intent.putExtra("dish_id", dish.getDishId());
        intent.putExtra("shop_name", shopNameMap.get(dish.getShopId()));
        startActivity(intent);
    }

    /** 刷新悬浮购物车条：件数与合计金额；空车时整条隐藏 */
    private void refreshCartBar() {
        if (cartBar == null) return; // 页面尚未初始化完成

        List<CartItem> items = CartStore.load(this);
        int count = CartStore.countTotalQty(items);

        if (count == 0) {
            cartBar.setVisibility(View.GONE); // 空车：整条不显示
            return;
        }

        cartBar.setVisibility(View.VISIBLE);
        tvCartCount.setText(String.valueOf(count));
        tvCartTotal.setText(getString(R.string.cart_total, CartStore.calcTotal(items)));
        tvCartHint.setText(getString(R.string.cart_item_count, count));
    }

    /** 加购：外卖校验、换食堂、反浪费提醒统一交给 CartActions 处理 */
    private void addToCart(Dish dish) {
        String name = shopNameMap.get(dish.getShopId());
        CartActions.add(this, dish, name == null ? "" : name, 1, items -> refreshCartBar());
    }
}
