package kr.co.candc.sales;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * 씨앤씨 영업 현황 — 안드로이드 앱
 * 화면·기능은 웹(GitHub Pages 시작 페이지 → Apps Script 웹 앱)에서 그대로 불러옴.
 * 웹을 고치면 앱도 바로 바뀌므로, 이 앱은 다시 설치할 일이 거의 없음.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://leekyounghyen-cmyk.github.io/cnc-sales-app/?app=android";
    private static final int FILE_REQ = 1001;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(getResources().getColor(R.color.bg, getTheme()));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUserAgentString(s.getUserAgentString() + " CncSalesApp/" + BuildConfigVersion());

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);   // Apps Script 화면(다른 주소)을 안에 띄우기 위해 필요

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req.getUrl(), req.isForMainFrame());
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) { super.onPageStarted(view, url, favicon); }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // 사진·파일 첨부
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), FILE_REQ);
                } catch (ActivityNotFoundException e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        // 엑셀 등 파일 받기 → 휴대폰 브라우저로 넘김
        web.setDownloadListener((url, ua, cd, mime, len) -> openOutside(Uri.parse(url)));

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    /** 앱 안에서 열 주소인지, 바깥(전화·카톡·다른 사이트)으로 보낼지 */
    private boolean handleUrl(Uri u, boolean mainFrame) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        String host = u.getHost() == null ? "" : u.getHost();
        if (scheme.equals("http") || scheme.equals("https")) {
            boolean ours = host.endsWith("github.io") || host.endsWith("google.com") || host.endsWith("googleusercontent.com")
                    || host.endsWith("gstatic.com") || host.endsWith("googleapis.com");
            if (ours || !mainFrame) return false;   // 앱 안에서 계속
            openOutside(u);
            return true;
        }
        if (scheme.equals("intent")) {
            try {
                Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // tel:, mailto:, sms:, kakaotalk: 등
        openOutside(u);
        return true;
    }

    private void openOutside(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) { web.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) { super.onBackPressed(); return; }
        lastBack = now;
        Toast.makeText(this, "한 번 더 누르면 앱이 닫혀요", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

    private boolean isOnline() {
        try {
            ConnectivityManager c = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo n = c.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) { return true; }
    }

    private String BuildConfigVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "1"; }
    }
}
