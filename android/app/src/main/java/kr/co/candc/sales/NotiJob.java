package kr.co.candc.sales;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 안드로이드 앱 알림: 5분마다(NotiAlarm) + 15분마다(백업) + 로그인·테스트 때 바로 앱 서버에 새 알림이 있는지 물어보고 휴대폰 알림을 띄움.
 * (안드로이드 앱은 웹 푸시를 못 받아서 이 방식 사용 — 구글 계정·추가 서비스 없이 동작)
 */
public class NotiJob extends JobService {

    static final String APP_URL = "https://script.google.com/macros/s/AKfycbw6Qsi7ZVZktH4xutPdGWHkYaM1u2nw6HGJDN3u8mBQVk0sbqBTpPliLXGwn20O_ix8/exec";
    static final String PREFS = "cnc";
    static final String CH = "noti2";   // 진동을 확실히 켠 새 알림 채널 (예전 채널 "noti" 는 지움)
    static final int JOB_ID = 7001, JOB_NOW = 7002;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, MODE_PRIVATE); }

    /** 로그인하면 15분마다 확인 시작, 로그아웃하면 멈춤 */
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (prefs(c).getString("token", "").isEmpty()) { js.cancel(JOB_ID); NotiAlarm.cancel(c); return; }
        NotiAlarm.next(c);   // 5분마다 확인 (빠른 알림)
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;   // 이미 예약됨
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, NotiJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /** 지금 한 번 확인 (테스트 알림 등) */
    static void runNow(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(c, NotiJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(3000)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { check(getApplicationContext()); } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }

    static void check(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String token = p.getString("token", "");
        if (token.isEmpty()) return;
        long since = p.getLong("since", 0);
        URL u = new URL(APP_URL + "?api=noti&token=" + URLEncoder.encode(token, "UTF-8") + "&since=" + since);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
            String line; while ((line = r.readLine()) != null) sb.append(line);
        }
        JSONObject j = new JSONObject(sb.toString());
        if (j.has("error")) return;
        JSONArray items = j.optJSONArray("items");
        boolean first = since == 0;   // 처음 연결할 때는 예전 알림을 한꺼번에 띄우지 않음
        if (items != null && items.length() > 0 && !first) {
            JSONObject x = items.getJSONObject(0);
            String title = x.optString("kind", "알림") + (x.optString("co").isEmpty() ? "" : " · " + x.optString("co"));
            String body = x.optString("text") + (items.length() > 1 ? "  (외 " + (items.length() - 1) + "건)" : "");
            show(c, x.optInt("id", 1), title, body);
        } else if (j.optBoolean("test")) {
            show(c, 1, "씨앤씨 알림 테스트", "휴대폰 알림이 잘 와요 ✓");
        }
        long last = j.optLong("last", since);
        if (first && last == 0) last = 1;
        p.edit().putLong("since", last).apply();
    }

    static void show(Context c, int id, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "출고·업무 알림", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("수리 출고 완료 등 씨앤씨 업무 알림");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{ 0, 300, 150, 300, 150, 500 });
            ch.enableLights(true);
            ch.setLightColor(0xFF0099DF);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            try { nm.deleteNotificationChannel("noti"); } catch (Exception ignored) { }
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF0099DF)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL).setVibrate(new long[]{ 0, 300, 150, 300, 150, 500 });
        try { nm.notify(id, b.build()); } catch (SecurityException ignored) { }
    }
}
