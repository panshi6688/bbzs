package com.bbzs.app;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

/**
 * 悬浮窗服务：管理悬浮图标和功能菜单
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "bbzs_floating_channel";

    private WindowManager windowManager;
    private View floatingIcon;
    private View floatingMenu;
    private WindowManager.LayoutParams iconParams;
    private WindowManager.LayoutParams menuParams;
    private boolean isMenuVisible = false;

    // 淘宝跳转链接
    private static final String URL_10W = "https://ku.m.taobao.com/search/m.html?disableNav=YES&channelSpmA=farm&channelSpmB=26936491&channelSrp=hudong_bbnc&bucketNo=21427416farmDynamicRuleVer1&ptsm=order.7980.75085&isDynamicFert=1&hdSecurity=1&epid=mm_12852562_1778064_110152850014&settleType=afterTime&settleTime=0&ffs=&adScene=tmall-farm-task-list&prismTrace=46316&farmMemkv=true&memkvKey=5387_1730952161004&activityId=100085&awardIndex=1&deliveryId=46316&implId=cloudsail_676_125400501040003_46316_0&samplingRate=10&sceneId=5387&hd_from_id=100085&spm=a2141.7631565.tbshopmod-series_title_link.0&spm=a2141.7631565.tbshopmod-photo_retouch.7&spm=&spm=a2141.7631565.tbshopmod-photo_retouch.7";
    private static final String URL_6W = "https://pages-fast.m.taobao.com/wow/z/app/ltao-fe/tjb-ssr/home?x-ssr=true&pha_manifest=default&spma=farm&spmb=xsmszywlq&sceneId=7506&deliveryId=68206&itemIds=675098470247&spm=a2141.7631565.tbshopmod-photo_retouch.5&spm=a2141.7631565.tbshopmod-photo_retouch.1";
    private static final String URL_5W = "https://ku.m.taobao.com/search/m2.html?ptsm=order.4973.70376&spm=a2141.7631565.tbshopmod-series_title_link.0&spm=a2141.7631565.tbshopmod-photo_retouch.10&spm=a2141.7631565.tbshopmod-photo_retouch_25951136023.7&spm=a2141.7631565.tbshopmod-photo_retouch.11";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            startForeground(1, buildNotification());

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            initFloatingIcon();
            initFloatingMenu();

            android.util.Log.d("FloatingService", "悬浮窗服务已启动");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "启动失败: " + e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingIcon != null) windowManager.removeView(floatingIcon);
        if (floatingMenu != null && floatingMenu.isAttachedToWindow()) {
            windowManager.removeView(floatingMenu);
        }
    }

    /**
     * 初始化悬浮图标
     */
    private void initFloatingIcon() {
        try {
            floatingIcon = LayoutInflater.from(this).inflate(R.layout.layout_floating_icon, null);

            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            iconParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            iconParams.gravity = Gravity.TOP | Gravity.START;
            iconParams.x = 0;
            iconParams.y = 300;

            windowManager.addView(floatingIcon, iconParams);
            setupIconTouchListener();

            android.util.Log.d("FloatingService", "悬浮图标已添加");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "添加悬浮图标失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置悬浮图标的触摸事件（拖动 + 点击）
     */
    private void setupIconTouchListener() {
        floatingIcon.setOnTouchListener(new View.OnTouchListener() {
            private float lastTouchX, lastTouchY;
            private float downX, downY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        downX = event.getRawX();
                        downY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        iconParams.x += (int) dx;
                        iconParams.y += (int) dy;
                        windowManager.updateViewLayout(floatingIcon, iconParams);
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        float totalDx = Math.abs(event.getRawX() - downX);
                        float totalDy = Math.abs(event.getRawY() - downY);
                        long duration = System.currentTimeMillis() - downTime;

                        // 判断是点击还是拖动
                        if (totalDx < 10 && totalDy < 10 && duration < 300) {
                            toggleMenu();
                        } else {
                            snapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 悬浮图标自动贴边
     */
    private void snapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int iconWidth = floatingIcon.getWidth();
        int targetX = (iconParams.x + iconWidth / 2 < screenWidth / 2) ? 0 : screenWidth - iconWidth;

        ValueAnimator animator = ValueAnimator.ofInt(iconParams.x, targetX);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            iconParams.x = (int) animation.getAnimatedValue();
            if (floatingIcon.isAttachedToWindow()) {
                windowManager.updateViewLayout(floatingIcon, iconParams);
            }
        });
        animator.start();
    }

    /**
     * 初始化功能菜单（不立即显示）
     */
    private void initFloatingMenu() {
        floatingMenu = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null);

        // 三个功能按钮点击事件
        floatingMenu.findViewById(R.id.btn_10w).setOnClickListener(v -> openTaobaoUrl(URL_10W));
        floatingMenu.findViewById(R.id.btn_6w).setOnClickListener(v -> openTaobaoUrl(URL_6W));
        floatingMenu.findViewById(R.id.btn_5w).setOnClickListener(v -> openTaobaoUrl(URL_5W));

        // 右上角更多菜单
        floatingMenu.findViewById(R.id.iv_menu_more).setOnClickListener(this::showPopupMenu);
    }

    /**
     * 切换菜单显示/隐藏
     */
    private void toggleMenu() {
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    /**
     * 显示功能菜单
     */
    private void showMenu() {
        if (isMenuVisible) return;

        try {
            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            menuParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.CENTER;

            // 添加点击外部关闭菜单的监听
            floatingMenu.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                    hideMenu();
                    return true;
                }
                return false;
            });

            windowManager.addView(floatingMenu, menuParams);
            isMenuVisible = true;

            android.util.Log.d("FloatingService", "功能菜单已显示");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "显示菜单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 隐藏功能菜单
     */
    private void hideMenu() {
        if (!isMenuVisible) return;
        try {
            if (floatingMenu != null && floatingMenu.isAttachedToWindow()) {
                windowManager.removeView(floatingMenu);
            }
            isMenuVisible = false;
            android.util.Log.d("FloatingService", "功能菜单已隐藏");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "隐藏菜单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 显示右上角弹出菜单
     */
    private void showPopupMenu(View anchor) {
        try {
            // 使用 ContextThemeWrapper 来确保 PopupMenu 能正常显示
            android.view.ContextThemeWrapper wrapper = new android.view.ContextThemeWrapper(this, R.style.Theme_Bbzs);
            PopupMenu popup = new PopupMenu(wrapper, anchor);
            popup.getMenuInflater().inflate(R.menu.menu_more, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_exit) {
                    stopSelf();
                    return true;
                } else if (id == R.id.menu_help) {
                    Toast.makeText(this, "点击按钮跳转到淘宝对应页面", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_about) {
                    Toast.makeText(this, "步步助手 v1.0", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
            android.util.Log.d("FloatingService", "弹出菜单已显示");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "显示弹出菜单失败: " + e.getMessage(), e);
            Toast.makeText(this, "菜单显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开淘宝链接
     */
    private void openTaobaoUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // 优先尝试用淘宝App打开
            intent.setPackage("com.taobao.taobao");
            startActivity(intent);
        } catch (Exception e) {
            // 淘宝未安装，用浏览器打开
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持悬浮窗服务运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台通知
     */
    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("步步助手")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(R.drawable.ic_floating)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
