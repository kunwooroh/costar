package com.voicetodo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 휴대폰 재부팅·앱 업데이트 후 알람 복구 (안드로이드는 재부팅 시 알람을 지움). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Reminders.rescheduleAll(ctx);
        }
    }
}
