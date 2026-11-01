package com.my.campus_canteen.ui.mine;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.my.campus_canteen.R;
import com.my.campus_canteen.data.AuthClient;
import com.my.campus_canteen.data.AuthStore;

/**
 * ============ 注册页 ============
 * 流程：本地校验（含两次密码一致）→ POST 服务器 /api/auth（action=register）
 *      → 服务器写入账号文件成功 → App 自动登录并返回。
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirm;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirm;
    private MaterialButton btnRegister;

    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        findViewById(R.id.btn_register_back).setOnClickListener(v -> finish());

        tilUsername = findViewById(R.id.til_register_username);
        tilPassword = findViewById(R.id.til_register_password);
        tilConfirm = findViewById(R.id.til_register_confirm);
        etUsername = findViewById(R.id.et_register_username);
        etPassword = findViewById(R.id.et_register_password);
        etConfirm = findViewById(R.id.et_register_confirm);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> doRegister());
        etConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doRegister();
                return true;
            }
            return false;
        });

        // 已有账号：直接返回上一页（登录页）
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
    }

    private void doRegister() {
        if (loading) {
            return;
        }
        tilUsername.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        String username = textOf(etUsername).trim();
        String password = textOf(etPassword);
        String confirm = textOf(etConfirm);

        if (!AuthClient.isValidUsername(username)) {
            tilUsername.setError(getString(R.string.auth_username_invalid));
            etUsername.requestFocus();
            return;
        }
        if (!AuthClient.isValidPassword(password)) {
            tilPassword.setError(getString(R.string.auth_password_invalid));
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            tilConfirm.setError(getString(R.string.auth_password_mismatch));
            etConfirm.requestFocus();
            return;
        }

        setLoading(true);
        AuthClient.register(this, username, password, (code, message) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setLoading(false);
            if (code == AuthClient.CODE_OK) {
                // 注册即登录：不用再让用户回登录页输一遍
                AuthStore.login(this, username);
                Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show();
                finish();
            } else if (code == 1) {
                tilUsername.setError(message);
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean value) {
        loading = value;
        btnRegister.setEnabled(!value);
        btnRegister.setText(value ? R.string.auth_loading_register : R.string.auth_register_btn);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }
}
