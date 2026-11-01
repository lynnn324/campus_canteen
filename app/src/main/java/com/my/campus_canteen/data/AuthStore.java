package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * ============ 登录状态（本地记住"谁登录了"）============
 * 说明：真正的账号密码校验在服务器；这里只保存登录成功后的用户名，
 *       让 App 关掉再打开还是登录状态。退出登录就清掉。
 */
public final class AuthStore {

    private static final String PREFS = "auth_prefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LOGIN_TIME = "login_time";

    private AuthStore() {
    }

    /** 是否已登录 */
    public static boolean isLoggedIn(Context c) {
        return !sp(c).getString(KEY_USERNAME, "").isEmpty();
    }

    /** 当前登录的用户名；没登录返回空串 */
    public static String username(Context c) {
        return sp(c).getString(KEY_USERNAME, "");
    }

    /** 登录成功 / 注册成功后调用：记住用户名 */
    public static void login(Context c, String username) {
        sp(c).edit()
                .putString(KEY_USERNAME, username)
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    }

    /** 退出登录 */
    public static void logout(Context c) {
        sp(c).edit()
                .remove(KEY_USERNAME)
                .remove(KEY_LOGIN_TIME)
                .apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
