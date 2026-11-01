package com.my.campus_canteen;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.my.campus_canteen.data.DataSync;
import com.my.campus_canteen.ui.cart.CartFragment;
import com.my.campus_canteen.ui.home.HomeFragment;
import com.my.campus_canteen.ui.mine.MineFragment;
import com.my.campus_canteen.ui.order.OrderFragment;

public class MainActivity extends AppCompatActivity {

    // 四个 Tab 的页面实例；第一次点进去才创建（懒加载，省内存）
    private Fragment homeFragment;
    private Fragment cartFragment;
    private Fragment orderFragment;
    private Fragment mineFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 进 App 先静默同步一次服务器数据：首页首帧用本地数据，下载成功后自动刷新
        DataSync.sync(getApplicationContext(), null);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // 进入 App：默认停在首页；若收到"支付成功"跳转信号，则直接展示订单页
        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra("go_order", false)) {
                switchToOrderTab();
            } else {
                switchFragment(new HomeFragment());
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
        }

        // 监听底部导航的点击，切换到对应页面
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                homeFragment = homeFragment == null ? new HomeFragment() : homeFragment;
                switchFragment(homeFragment);
                return true;
            } else if (id == R.id.nav_cart) {
                cartFragment = cartFragment == null ? new CartFragment() : cartFragment;
                switchFragment(cartFragment);
                return true;
            } else if (id == R.id.nav_order) {
                orderFragment = orderFragment == null ? new OrderFragment() : orderFragment;
                switchFragment(orderFragment);
                return true;
            } else if (id == R.id.nav_mine) {
                mineFragment = mineFragment == null ? new MineFragment() : mineFragment;
                switchFragment(mineFragment);
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void switchToOrderTab() {
        orderFragment = orderFragment == null ? new OrderFragment() : orderFragment;
        switchFragment(orderFragment);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_order);
    }

    /**
     * MainActivity 已经存在（比如正在首页）时，新 Intent 不会重建页面，
     * 而是走 onNewIntent。这里同样处理"支付成功"信号，避免漏接。
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("go_order", false)) {
            switchToOrderTab();
        }
    }
}
