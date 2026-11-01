package com.my.campus_canteen.data;

import android.os.CountDownTimer;
import android.widget.TextView;

public class EstimateCountDown {

    private EstimateCountDown() {
        // 工具类：私有构造
    }

    /** 估算出餐分钟数：基础 5 分钟 + 每份菜 1 分钟 */
    public static int estimateMinutes(int dishCount) {
        return 5 + dishCount;
    }

    /**
     * 启动倒计时并实时刷新到 TextView。
     * 返回 CountDownTimer 给调用方保存，离开页面时要 cancel() 释放。
     */
    public static CountDownTimer start(int totalMinutes, TextView tvCountDown) {
        long totalMillis = totalMinutes * 60 * 1000L; // 分钟 → 毫秒
        return new CountDownTimer(totalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long remainSec = millisUntilFinished / 1000;
                long minute = remainSec / 60;
                long second = remainSec % 60;
                tvCountDown.setText("预计还需 " + minute + "分" + second + "秒 出餐");
            }

            @Override
            public void onFinish() {
                tvCountDown.setText("出餐完成，可以取餐啦");
            }
        }.start();
    }
}
