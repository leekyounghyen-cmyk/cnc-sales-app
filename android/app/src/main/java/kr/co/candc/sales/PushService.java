package kr.co.candc.sales;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * 출고 알림 즉시 받기 (Firebase). 서버가 "새 알림 있음" 신호를 보내면
 * 바로 앱 서버에서 최신 알림을 받아와 진동 알림을 띄움 (5분 확인과 같은 방식이라 중복 없음).
 */
public class PushService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage msg) {
        try { NotiJob.check(getApplicationContext()); }
        catch (Exception e) {
            String t = msg.getData().get("title"), b = msg.getData().get("body");
            if (t != null) NotiJob.show(getApplicationContext(), 1, t, b == null ? "" : b);
        }
    }

    @Override
    public void onNewToken(String token) {
        NotiJob.regFcm(getApplicationContext(), token);
    }
}
