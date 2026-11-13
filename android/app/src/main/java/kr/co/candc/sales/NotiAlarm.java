package kr.co.candc.sales;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * 5분마다 새 알림 확인 (15분짜리 JobScheduler 보다 빠르게).
 * 절전(잠자기) 중에도 깨워서 확인하지만, 안드로이드 규칙상 깊은 절전 땐 9~15분 간격이 될 수 있음.
 * 휴대폰을 다시 켜면(부팅) 자동으로 다시 시작.
 */
public class NotiAlarm extends BroadcastReceiver {
    static final long EVERY = 5 * 60 * 1000L;

    static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, 7101, new Intent(c, NotiAlarm.class).setAction("kr.co.candc.sales.CHECK"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void next(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (NotiJob.prefs(c).getString("token", "").isEmpty()) { am.cancel(pi(c)); return; }
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + EVERY, pi(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) NotiJob.schedule(c);
                else NotiJob.check(c);
            } catch (Exception ignored) { }
            next(c);
            pr.finish();
        }).start();
    }
}
