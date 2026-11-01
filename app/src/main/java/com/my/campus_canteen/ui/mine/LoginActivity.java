package com.my.campus_canteen.ui.mine;

import android.content.Intent;
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
 * ============ 登录页 ============
 * 流程：本地校验格式 → POST 服务器 /api/auth（action=login）→ 成功后记住用户名并返回。
 * 服务器没开时提示先启动 Tomcat 或 Node 服务器。
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;

    private boolean loading; // 请求进行中：按钮禁用，防止连续点击发多次请求

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        findViewById(R.id.btn_login_back).setOnClickListener(v -> finish());

        tilUsername = findViewById(R.id.til_login_username);
        tilPassword = findViewById(R.id.til_login_password);
        etUsername = findViewById(R.id.et_login_username);
        etPassword = findViewById(R.id.et_login_password);
        btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> doLogin());
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin();
                return true;
            }
            return false;
        });

        // 去注册：注册成功后 RegisterActivity 会结束，回到这里由 onResume 自动收起
        findViewById(R.id.tv_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从注册页回来且已经注册成功（登录态已写入）：登录页没有意义了，直接关掉
        if (AuthStore.isLoggedIn(this)) {
            finish();
        }
    }

    private void doLogin() {
        if (loading) {
            return;
        }
        tilUsername.setError(null);
        tilPassword.setError(null);

        String username = textOf(etUsername).trim();
        String password = textOf(etPassword);
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

        setLoading(true);
        AuthClient.login(this, username, password, (code, message) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setLoading(false);
            if (code == AuthClient.CODE_OK) {
                AuthStore.login(this, username);
                Toast.makeText(this, R.string.auth_login_success, Toast.LENGTH_SHORT).show();
                finish();
            } else if (code == 2) {
                // 账号或密码错误：把服务器的提示挂到密码框下面
                tilPassword.setError(message);
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean value) {
        loading = value;
        btnLogin.setEnabled(!value);
        btnLogin.setText(value ? R.string.auth_loading_login : R.string.auth_login_btn);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }
}
