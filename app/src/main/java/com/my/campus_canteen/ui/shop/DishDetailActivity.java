package com.my.campus_canteen.ui.shop;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.my.campus_canteen.R;
import com.my.campus_canteen.data.CartStore;
import com.my.campus_canteen.data.JsonParser;
import com.my.campus_canteen.model.CartItem;
import com.my.campus_canteen.model.Dish;
import com.my.campus_canteen.ui.cart.CartActions;

import java.util.List;

public class DishDetailActivity extends AppCompatActivity {

    private Dish dish;
    private String shopName = "";   // 菜品所属档口名（加购时写进购物车）
    private int quantity = 1;
    private TextView tvQty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dish_detail);

        // 标题栏返回箭头：关闭本页，回到列表
        findViewById(R.id.btn_dish_detail_back).setOnClickListener(v -> finish());

        // 接收列表页传来的菜品编号与档口名
        int dishId = getIntent().getIntExtra("dish_id", -1);
        String extraName = getIntent().getStringExtra("shop_name");
        shopName = extraName == null ? "" : extraName;

        dish = findDish(dishId);
        if (dish == null) {
            // 数据被删/传参异常：提示后直接退回，避免空页面
            Toast.makeText(this, R.string.dish_detail_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (shopName.isEmpty()) {
            shopName = CartActions.shopNameOf(this, dish.getShopId()); // 分类页没传名字时按编号补查
        }

        bindDishInfo();
        bindQuantityAndAdd();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCartSummary(); // 从结算页回来时同步购物车件数与金额
    }

    /** 从 dishes.json 里按编号找出这道菜 */
    private Dish findDish(int dishId) {
        for (Dish d : JsonParser.parseDishes(this)) {
            if (d.getDishId() == dishId) {
                return d;
            }
        }
        return null;
    }
    /** 填充大图、菜名、标签、种类来源、一句话描述、价格与支持方式 */
    private void bindDishInfo() {
        ImageView img = findViewById(R.id.img_detail_dish);
        bindImage(img, dish);

        TextView tvName = findViewById(R.id.tv_detail_name);
        tvName.setText(dish.getName());

        // 趣味标签：有 label 才显示
        TextView tvLabel = findViewById(R.id.tv_detail_label);
        String label = dish.getLabel();
        if (label == null || label.isEmpty()) {
            tvLabel.setVisibility(View.GONE);
        } else {
            tvLabel.setVisibility(View.VISIBLE);
            tvLabel.setText(label);
        }

        // 种类 + 来源档口
        TextView tvMeta = findViewById(R.id.tv_detail_meta);
        if (shopName.isEmpty()) {
            tvMeta.setText(getString(R.string.dish_detail_meta_plain, dish.getType()));
        } else {
            tvMeta.setText(getString(R.string.dish_detail_meta, dish.getType(), shopName));
        }

        // 一句话描述：数据里有才显示，没有就整段隐藏（不留空行）
        TextView tvDesc = findViewById(R.id.tv_detail_desc);
        String desc = dish.getDesc();
        if (desc == null || desc.isEmpty()) {
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(desc);
        }

        TextView tvPrice = findViewById(R.id.tv_detail_price);
        tvPrice.setText("¥" + dish.getPrice());

        TextView tvMode = findViewById(R.id.tv_detail_mode);
        tvMode.setText(modeText());

        // 分量偏少提示：数据里 smallPortionWarn 为 true 才亮出来
        TextView tvSmall = findViewById(R.id.tv_detail_small);
        tvSmall.setVisibility(dish.isSmallPortionWarn() ? View.VISIBLE : View.GONE);
    }

    /** 支持方式文案：都支持 / 仅外卖 / 仅堂食 / 都不支持 */
    private int modeText() {
        if (dish.isCanTakeout() && dish.isCanDineIn()) return R.string.dish_detail_mode_all;
        if (dish.isCanTakeout()) return R.string.dish_detail_mode_takeout;
        if (dish.isCanDineIn()) return R.string.dish_detail_mode_dinein;
        return R.string.dish_detail_mode_none;
    }

    /** 数量加减 + 加入购物车按钮 */
    private void bindQuantityAndAdd() {
        tvQty = findViewById(R.id.tv_detail_qty);
        tvQty.setText(String.valueOf(quantity));

        TextView btnAdd = findViewById(R.id.btn_add_to_cart);
        btnAdd.setText(getString(R.string.dish_detail_add, String.valueOf(dish.getPrice())));

        // 减号：最少 1 份，再减就到底不动（与购物车里的最小份数规则一致）
        findViewById(R.id.btn_qty_minus).setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQty.setText(String.valueOf(quantity));
            }
        });

        // 加号：最多 99 份，防止误触后数量离谱
        findViewById(R.id.btn_qty_plus).setOnClickListener(v -> {
            if (quantity < 99) {
                quantity++;
                tvQty.setText(String.valueOf(quantity));
            }
        });

        btnAdd.setOnClickListener(v -> addToCart());
    }

    /** 刷新底部提示：购物车里已有多少件、合计多少钱 */
    private void refreshCartSummary() {
        TextView tvSummary = findViewById(R.id.tv_detail_cart_summary);
        if (tvSummary == null) return;

        List<CartItem> items = CartStore.load(this);
        int count = CartStore.countTotalQty(items);

        if (count == 0) {
            tvSummary.setVisibility(View.GONE);
        } else {
            tvSummary.setVisibility(View.VISIBLE);
            tvSummary.setText(getString(R.string.dish_detail_cart_summary,
                    count, CartStore.calcTotal(items)));
        }
    }

    /** 加购：按选好的份数交给 CartActions，成功后把数量重置为 1 */
    private void addToCart() {
        CartActions.add(this, dish, shopName, quantity, items -> {
            quantity = 1;
            tvQty.setText("1");
            refreshCartSummary();
        });
    }

    /** 绑定大图：image 字段存的是 drawable 资源名，查不到就用通用图标兜底 */
    private void bindImage(ImageView img, Dish dish) {
        int resId = R.drawable.ic_meal;
        String imgName = dish.getImage();
        if (imgName != null && !imgName.isEmpty()) {
            int id = getResources().getIdentifier(imgName, "drawable", getPackageName());
            if (id != 0) {
                resId = id;
            }
        }
        img.setImageResource(resId);
    }
}
