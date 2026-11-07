package com.qg.reshade;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    static { 
        try { System.loadLibrary("qgreshade"); } catch (Exception e) {}
    }
    private native String probeKernel();
    private static final int SHIZUKU_REQUEST_CODE = 1003;
    private static final int OVERLAY_REQUEST_CODE = 1004;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST_CODE);
            Toast.makeText(this, "请授予悬浮窗权限以加载 AI 现实滤镜", Toast.LENGTH_LONG).show();
            return;
        }

        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
                return;
            }
        } else {
            Toast.makeText(this, "Shizuku 未启动，降级运行基础滤镜", Toast.LENGTH_SHORT).show();
        }

        String probeResult = "Shizuku 未授权";
        try { probeResult = probeKernel(); } catch (Throwable t) {}
        
        setContentView(new FieldRenderer(this, probeResult));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST_CODE) recreate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == SHIZUKU_REQUEST_CODE) recreate();
    }
}