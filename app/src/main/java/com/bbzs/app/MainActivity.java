package com.bbzs.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 主Activity：检查悬浮窗权限并启动悬浮服务
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkOverlayPermission();
    }

    /**
     * 检查悬浮窗权限
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog();
        } else {
            startFloatingService();
            finish();
        }
    }

    /**
     * 显示权限申请对话框
     */
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("本应用需要悬浮窗权限才能正常使用，请在设置中开启。")
                .setCancelable(false)
                .setPositiveButton("去开启", (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                })
                .setNegativeButton("退出", (dialog, which) -> finish())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService();
                finish();
            } else {
                showPermissionDialog();
            }
        }
    }

    /**
     * 启动悬浮窗服务
     */
    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}

