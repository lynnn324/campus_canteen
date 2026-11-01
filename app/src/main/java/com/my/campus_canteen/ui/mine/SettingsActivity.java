package com.my.campus_canteen.ui.mine;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.my.campus_canteen.R;
import com.my.campus_canteen.data.DataSync;

public class SettingsActivity extends AppCompatActivity implements DataSync.Listener {

    private TextView tvDataSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 返回箭头
        findViewById(R.id.btn_setting_back).setOnClickListener(v -> finish());

        // 关于与版本：弹窗展示
        findViewById(R.id.row_about).setOnClickListener(v -> showAbout());

        // 数据源：显示当前用的是服务器数据还是本地兜底，点一下重新同步
        tvDataSource = findViewById(R.id.tv_data_source);
        tvDataSource.setText(DataSync.sourceLabel(this));
        findViewById(R.id.row_data_source).setOnClickListener(v -> {
            tvDataSource.setText(R.string.data_source_syncing);
            DataSync.sync(this, this);
        });
    }

    /** 关于与版本：App 名 + 实时版本号 */
    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_about)
                .setMessage(getString(R.string.about_msg, appVersion()))
                .setPositiveButton(R.string.about_ok, null)
                .show();
    }

    /** 读取当前安装包的版本号（改 build.gradle 的 versionName 后这里自动跟着变） */
    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return ""; // 极端情况兜底，不崩溃
        }
    }

    @Override
    public void onSyncResult(boolean success) {
        if (tvDataSource == null) {
            return;
        }
        tvDataSource.setText(success
                ? DataSync.sourceLabel(this)
                : getString(R.string.data_source_fail));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DataSync.removeListener(this);
    }
}
