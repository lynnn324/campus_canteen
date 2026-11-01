package com.my.campus_canteen.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.my.campus_canteen.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * ============ 登录 / 注册（App → 服务器）============
 * 接口：POST {base}/api/auth    参数 action=login|register & username & password
 * 服务器有两套实现，谁开着就找谁：
 *   Tomcat：http://127.0.0.1:8080/campus-canteen/api/auth.jsp
 *   Node  ：http://127.0.0.1:8080/api/auth
 * 都没开时换模拟器专用地址 10.0.2.2 再试一遍。
 * 特点：只用系统自带的 HttpURLConnection，不引第三方网络库，回调已切回主线程。
 */
public final class AuthClient {

    /** 业务成功 */
    public static final int CODE_OK = 0;
    /** 连不上任何服务器（本地自定义，服务器不会返回这个数） */
    public static final int CODE_NETWORK = -1;

    public interface Callback {
        void onResult(int code, String message);
    }

    // 地址 + 接口路径：Tomcat 的 JSP 与 Node 的路由写法不一样，所以分开配对
    private static final String[][] TARGETS = {
            {"http://127.0.0.1:8080/campus-canteen", "/api/auth.jsp"},
            {"http://127.0.0.1:8080", "/api/auth"},
            {"http://10.0.2.2:8080/campus-canteen", "/api/auth.jsp"},
            {"http://10.0.2.2:8080", "/api/auth"}
    };

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AuthClient() {
    }

    /** 用户名规则：3-20 位字母、数字或下划线（跟服务器保持一致） */
    public static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]{3,20}");
    }

    /** 密码规则：6-20 位可见字符，不含空格（跟服务器保持一致） */
    public static boolean isValidPassword(String password) {
        return password != null && password.matches("[\\x21-\\x7E]{6,20}");
    }

    public static void login(Context context, String username, String password, Callback callback) {
        request(context, "login", username, password, callback);
    }

    public static void register(Context context, String username, String password, Callback callback) {
        request(context, "register", username, password, callback);
    }

    /** 后台线程依次尝试各个服务器地址，结果切回主线程回调 */
    private static void request(Context context, String action, String username,
                                String password, Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            int code = CODE_NETWORK;
            String message = app.getString(R.string.auth_network_error);
            for (String[] target : TARGETS) {
                Result result = post(target[0] + target[1], action, username, password);
                if (result == null) {
                    continue; // 这个地址没开、或不是提供登录接口的服务，换下一个
                }
                code = result.code;
                message = result.message;
                break; // 服务器已经给出明确业务结果，不再换地址
            }
            final int finalCode = code;
            final String finalMessage = message;
            MAIN.post(() -> callback.onResult(finalCode, finalMessage));
        }, "auth-request").start();
    }

    /** 发一次 POST 表单；返回 null 表示"这个地址不可用，应换下一个" */
    private static Result post(String url, String action, String username, String password) {
        HttpURLConnection conn = null;
        try {
            String body = "action=" + URLEncoder.encode(action, "UTF-8")
                    + "&username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8");

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(3500);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND
                    || status == HttpURLConnection.HTTP_BAD_METHOD) {
                return null; // 这个地址没有登录接口（比如只开了 Node / 只开了 Tomcat）
            }
            if (status != HttpURLConnection.HTTP_OK) {
                return null; // 5xx 等异常也换下一个地址试试
            }

            String text = readAll(conn.getInputStream());
            JSONObject json = new JSONObject(text);
            if (!json.has("code")) {
                return null; // 返回了网页而不是接口数据，说明地址不对
            }
            return new Result(json.optInt("code", CODE_NETWORK),
                    json.optString("message", ""));
        } catch (Exception e) {
            return null; // 连接超时、DNS 失败、JSON 坏了：都视为该地址不可用
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static final class Result {
        final int code;
        final String message;

        Result(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
