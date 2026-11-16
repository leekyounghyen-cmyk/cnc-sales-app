package kr.co.candc.sales;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

/**
 * 앱 안 카메라 (빌드 9)
 * 사진 칸을 누르면 바로 이 화면이 열림: 가운데 ⚪ 촬영 · 왼쪽 🖼 앨범 · 오른쪽 🔄 앞/뒤 전환 · 위 ✕ 닫기
 * 결과: RESULT_OK = 찍은 사진(out 파일), RES_ALBUM = 앨범에서 고르기, 그 외 = 취소
 */
public class CamActivity extends ComponentActivity {

    static final int RES_ALBUM = 2;
    private static final int PERM = 21;

    private PreviewView pv;
    private ImageCapture cap;
    private ProcessCameraProvider prov;
    private boolean back = true;
    private boolean busy = false;
    private File out;
    private View shutter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        out = new File(getIntent().getStringExtra("out"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        pv = new PreviewView(this);
        pv.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        root.addView(pv, new FrameLayout.LayoutParams(-1, -1));

        // 위: 닫기
        TextView close = label("✕", 26);
        close.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        FrameLayout.LayoutParams lc = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.START);
        lc.topMargin = dp(8); lc.leftMargin = dp(8);
        root.addView(close, lc);

        // 아래: 앨범 · 촬영 · 전환
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0x99000000);
        bar.setPadding(dp(20), dp(18), dp(20), dp(26));

        TextView album = label("🖼\n앨범", 15);
        album.setOnClickListener(v -> { setResult(RES_ALBUM); finish(); });
        bar.addView(album, new LinearLayout.LayoutParams(0, dp(72), 1));

        FrameLayout sw = new FrameLayout(this);
        shutter = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.WHITE);
        ring.setStroke(dp(5), 0xFFBBBBBB);
        shutter.setBackground(ring);
        shutter.setContentDescription("촬영");
        shutter.setOnClickListener(v -> take());
        sw.addView(shutter, new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER));
        bar.addView(sw, new LinearLayout.LayoutParams(0, dp(80), 1));

        TextView flip = label("🔄\n전환", 15);
        flip.setOnClickListener(v -> { back = !back; bind(); });
        bar.addView(flip, new LinearLayout.LayoutParams(0, dp(72), 1));

        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start();
        else requestPermissions(new String[]{ Manifest.permission.CAMERA }, PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code != PERM) return;
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) start();
        else {
            Toast.makeText(this, "카메라 권한이 없어서 앨범을 열게요 (설정 → 앱 → 씨앤씨 → 권한에서 켤 수 있어요)", Toast.LENGTH_LONG).show();
            setResult(RES_ALBUM); finish();
        }
    }

    private void start() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try { prov = f.get(); bind(); }
            catch (Exception e) { fail(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bind() {
        if (prov == null) return;
        try {
            CameraSelector sel = back ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
            if (!prov.hasCamera(sel)) { back = !back; sel = back ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA; }
            Preview pre = new Preview.Builder().build();
            pre.setSurfaceProvider(pv.getSurfaceProvider());
            cap = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                    .build();
            prov.unbindAll();
            prov.bindToLifecycle(this, sel, pre, cap);
        } catch (Exception e) { fail(); }
    }

    private void take() {
        if (cap == null || busy) return;
        busy = true;
        shutter.setAlpha(0.4f);
        ImageCapture.OutputFileOptions o = new ImageCapture.OutputFileOptions.Builder(out).build();
        cap.takePicture(o, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults r) {
                setResult(RESULT_OK); finish();
            }
            @Override public void onError(ImageCaptureException e) {
                busy = false; shutter.setAlpha(1f);
                Toast.makeText(CamActivity.this, "촬영에 실패했어요. 다시 눌러 주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fail() {
        Toast.makeText(this, "카메라를 열 수 없어서 앨범을 열게요", Toast.LENGTH_SHORT).show();
        setResult(RES_ALBUM); finish();
    }

    private TextView label(String t, int sp) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
