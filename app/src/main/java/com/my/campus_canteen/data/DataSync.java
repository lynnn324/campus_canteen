package com.my.campus_canteen.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.my.campus_canteen.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ============ 数据同步（App ← 服务器）============
 * 作用：从校园食堂数据服务器下载 shops.json / dishes.json，存到本地缓存目录；
 *       下次启动时 JsonParser 优先读缓存，断网或服务器没开就自动回落到 assets。
 * 特点：只用系统自带的 HttpURLConnection，不引任何第三方网络库。
 */
public final class DataSync {

    /** 同步结果回调（已切回主线程，可直接刷新界面） */
    public interface Listener {
        void onSyncResult(boolean success);
    }

    private static final String PREF = "data_sync";
    private static final String KEY_VERSION = "version";
    private static final String KEY_TIME = "time";
    private static final String KEY_HOST = "host";

    private static final String CACHE_DIR = "remote";
    private static final String SHOPS_FILE = "shops.json";
    private static final String DISHES_FILE = "dishes.json";

    // 数据接口候选：教材的 Tomcat 部署（/campus-canteen 上下文 + .json）排在前面，
    // 再试轻量 Node 服务的根路径；本机不通时换模拟器专用地址 10.0.2.2
    private static final String[][] TARGETS = {
            {"http://127.0.0.1:8080/campus-canteen", "/api/shops.json", "/api/dishes.json"},
            {"http://127.0.0.1:8080", "/api/shops", "/api/dishes"},
            {"http://10.0.2.2:8080/campus-canteen", "/api/shops.json", "/api/dishes.json"},
            {"http://10.0.2.2:8080", "/api/shops", "/api/dishes"}
    };

    private static final Object LOCK = new Object();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean syncing = false;

    private DataSync() {
    }

    /**
     * 发起一次同步（后台线程执行，重复调用不会并发）。
     *
     * @param listener 可为 null；结果回来后会回调，记得在页面销毁时 removeListener
     */
    public static void sync(Context context, Listener listener) {
        synchronized (LOCK) {
            if (listener != null && !LISTENERS.contains(listener)) {
                LISTENERS.add(listener);
            }
            if (syncing) {
                return; // 已经在同步，等这轮结束会一起通知
            }
            syncing = true;
        }

        final Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean ok = doSync(app);
            List<Listener> copy;
            synchronized (LOCK) {
                syncing = false;
                copy = new ArrayList<>(LISTENERS);
                LISTENERS.clear();
            }
            if (!copy.isEmpty()) {
                MAIN.post(() -> {
                    for (Listener l : copy) {
                        l.onSyncResult(ok);
                    }
                });
            }
        }, "data-sync").start();
    }

    /** 页面销毁时取消监听，避免回调打到已销毁的界面 */
    public static void removeListener(Listener listener) {
        synchronized (LOCK) {
            LISTENERS.remove(listener);
        }
    }

    /** 读取服务器缓存文件；没有缓存或读失败返回 null */
    public static String readCache(Context context, String fileName) {
        File f = new File(cacheDir(context), fileName);
        if (!f.isFile() || f.length() == 0) {
            return null;
        }
        try (InputStream is = new FileInputStream(f)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /** 数据来源文案，用于设置页展示 */
    public static String sourceLabel(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String version = sp.getString(KEY_VERSION, "");
        String host = sp.getString(KEY_HOST, "");
        String time = sp.getString(KEY_TIME, "");
        if (host.isEmpty() || version.isEmpty()) {
            return context.getString(R.string.data_source_local);
        }
        String hostShort = host.replace("http://", "");
        String timeShort = time;
        try {
            long ts = Long.parseLong(time);
            timeShort = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ts));
        } catch (NumberFormatException ignored) {
        }
        return context.getString(R.string.data_source_server,
                version, hostShort, timeShort);
    }

    // ---------- 内部实现 ----------

    private static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static boolean doSync(Context context) {
        for (String[] target : TARGETS) {
            String base = target[0];
            try {
                String shops = get(base, target[1]);
                String dishes = get(base, target[2]);
                // 先校验再落盘：宁可保留旧缓存，也不要把坏数据写进去
                String version = validate(shops, "shops");
                validate(dishes, "dishes");

                File dir = cacheDir(context);
                writeFile(dir, SHOPS_FILE, shops);
                writeFile(dir, DISHES_FILE, dishes);

                context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString(KEY_VERSION, version)
                        .putString(KEY_HOST, base)
                        .putString(KEY_TIME, String.valueOf(System.currentTimeMillis()))
                        .apply();
                return true;
            } catch (IOException | JSONException ignored) {
                // 这台地址不通就换下一个；全都不通就返回 false，App 继续用本地数据
            }
        }
        return false;
    }

    private static String get(String base, String api) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + api).openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(3500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream is = conn.getInputStream()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 校验接口数据，返回数据版本号（没带版本就给空串） */
    private static String validate(String json, String key) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray(key);
        if (arr.length() == 0) {
            throw new JSONException(key + " 为空");
        }
        return root.optString("version", "");
    }

    /** 先写临时文件再改名，避免下载一半的文件被当成有效缓存 */
    private static void writeFile(File dir, String name, String text) throws IOException {
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        File dst = new File(dir, name);
        if (dst.exists() && !dst.delete()) {
            throw new IOException("无法覆盖缓存文件");
        }
        if (!tmp.renameTo(dst)) {
            throw new IOException("无法写入缓存文件");
        }
    }
}
