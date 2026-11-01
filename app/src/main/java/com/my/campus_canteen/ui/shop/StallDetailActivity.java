package com.my.campus_canteen.ui.shop;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
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
import com.my.campus_canteen.ui.cart.CartActions;
import com.my.campus_canteen.ui.cart.CartPreviewDialog;
import com.my.campus_canteen.ui.order.CheckoutActivity;

import java.util.ArrayList;
import java.util.List;

public class StallDetailActivity extends AppCompatActivity {

    private TextureView videoKitchen;    // 透明厨房视频（TextureView，模拟器也能出画面）
    private TextView tvVideoFallback;   // 视频加载失败时的兜底文案
    private MediaPlayer kitchenPlayer;  // 视频播放器（手动管理生命周期）
    private String shopName = "";       // 当前档口名（写进购物车做分组标题）
    private int shopId = -1;            // 当前档口编号（悬浮购物车只统计本档口的菜）
    private View cartBar;
    private TextView tvCartTotal;
    private TextView tvCartHint;
    private TextView tvCartCount;

    /**
     * 预留：档口后厨的实时监控流地址（RTSP）。
     * 正式接入时填成 "rtsp://..."，留空则播放本地示例素材；
     * 角标文案会据此在"演示画面 / 直播中"之间自动切换。
     * 说明：Android MediaPlayer 原生支持 RTSP，不需要引入第三方播放器；
     * 若将来换成 HLS/FLV 流，需要额外依赖，课程项目暂不引入。
     */
    private static final String LIVE_STREAM_URL = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stall_detail);

        // 标题栏返回箭头：点一下关闭本页，回到首页
        findViewById(R.id.btn_stall_back).setOnClickListener(v -> finish());

        // 接收 HomeFragment 用 Intent "快递包" 传来的档口名与 id
        Intent intent = getIntent();
        shopName = intent.getStringExtra("shop_name");
        shopId = intent.getIntExtra("shop_id", -1);

        TextView tvTitle = findViewById(R.id.tv_shop_title);
        if (shopName != null) {
            tvTitle.setText(shopName);
        }

        // ① 播放厨房视频（透明厨房）：有实时流播实时流，没有就播本地示例素材
        videoKitchen = findViewById(R.id.video_kitchen);
        tvVideoFallback = findViewById(R.id.tv_video_fallback);
        // 角标文案跟着数据源走：没接监控时写明"演示画面"，不把示例素材当成实时画面
        TextView tvKitchenBadge = findViewById(R.id.tv_kitchen_badge);
        tvKitchenBadge.setText(LIVE_STREAM_URL.isEmpty()
                ? R.string.kitchen_badge_demo : R.string.kitchen_badge_live);
        playKitchenVideo();

        // ② 展示该档口菜品：从 dishes.json 过滤出 shopId 相符的菜
        List<Dish> allDishes = JsonParser.parseDishes(this);
        List<Dish> shopDishes = new ArrayList<>();
        for (Dish dish : allDishes) {
            if (dish.getShopId() == shopId) {
                shopDishes.add(dish); // 只留本档口的菜
            }
        }

        RecyclerView rvDish = findViewById(R.id.rv_dish_list);
        rvDish.setLayoutManager(new LinearLayoutManager(this));
        // 传入加购回调：点"加购"就执行 addToCart
        rvDish.setAdapter(new DishAdapter(shopDishes, this::addToCart, null, this::openDishDetail));

        // 空态：该档口没有菜时显示提示
        TextView tvDishEmpty = findViewById(R.id.tv_dish_empty);
        if (shopDishes.isEmpty()) {
            tvDishEmpty.setVisibility(View.VISIBLE);
            rvDish.setVisibility(View.GONE);
        }

        // ③ 美团式悬浮购物车：底部浮条，加购后实时刷新金额与件数
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

    /** 点某道菜：进入菜品详情页，携带菜品编号和当前档口名 */
    private void openDishDetail(Dish dish) {
        Intent intent = new Intent(this, DishDetailActivity.class);
        intent.putExtra("dish_id", dish.getDishId());
        intent.putExtra("shop_name", shopName);
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
        CartActions.add(this, dish, shopName, 1, items -> refreshCartBar());
    }

    private void playKitchenVideo() {
        videoKitchen.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                startKitchenPlayer(surfaceTexture); // 表面就绪 → 启动播放
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                // 尺寸变化无需处理
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                releaseKitchenPlayer(); // 页面销毁表面时释放播放器
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                // 每帧回调，无需处理
            }
        });
    }

    /** 创建并启动播放器：把 raw 视频解码后送到 TextureView 的表面。 */
    private void startKitchenPlayer(SurfaceTexture surfaceTexture) {
        releaseKitchenPlayer(); // 先释放旧的，避免重复播放
        try {
            // 实时监控流优先；没配地址就退回本地示例素材（演示用）
            String source = LIVE_STREAM_URL.isEmpty()
                    ? "android.resource://" + getPackageName() + "/" + R.raw.kitchen_live
                    : LIVE_STREAM_URL;
            Uri uri = Uri.parse(source);
            kitchenPlayer = new MediaPlayer();
            kitchenPlayer.setDataSource(this, uri);
            kitchenPlayer.setSurface(new Surface(surfaceTexture));
            kitchenPlayer.setLooping(true); // 透明厨房=一直看着后厨

            // 加载/播放出错时兜底
            kitchenPlayer.setOnErrorListener((mp, what, extra) -> {
                showVideoFallback();
                return true; // 表示已处理，不再弹系统错误
            });
            // 准备好后自动开始
            kitchenPlayer.setOnPreparedListener(MediaPlayer::start);
            kitchenPlayer.prepareAsync();
        } catch (Exception e) {
            // 资源异常等极端情况：兜底显示文案
            showVideoFallback();
        }
    }

    /** 释放播放器，防止内存泄漏。 */
    private void releaseKitchenPlayer() {
        if (kitchenPlayer != null) {
            try {
                kitchenPlayer.release();
            } catch (Exception ignored) {
                // 释放失败可忽略
            }
            kitchenPlayer = null;
        }
    }

    /** 播放失败兜底：隐藏视频画面，显示提示文案。 */
    private void showVideoFallback() {
        videoKitchen.setVisibility(View.GONE);
        tvVideoFallback.setVisibility(View.VISIBLE);
    }

    // 生命周期钩子：离开页面暂停播放器，避免资源占用；回到页面再继续播
    @Override
    protected void onPause() {
        super.onPause();
        if (kitchenPlayer != null) {
            try {
                if (kitchenPlayer.isPlaying()) {
                    kitchenPlayer.pause();
                }
            } catch (Exception ignored) {
                // 未就绪时忽略
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCartBar(); // 从购物车/结算页回来时同步最新件数与金额
        // 从别的页面回来时接着播（视频加载失败已被隐藏的情况不重试，避免反复报错）
        if (kitchenPlayer != null && videoKitchen.getVisibility() == View.VISIBLE) {
            try {
                if (!kitchenPlayer.isPlaying()) {
                    kitchenPlayer.start();
                }
            } catch (Exception ignored) {
                // 未就绪时忽略
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseKitchenPlayer(); // 页面销毁时彻底释放
    }
}
