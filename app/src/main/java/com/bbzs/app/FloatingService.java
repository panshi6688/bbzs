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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

import com.google.android.material.button.MaterialButton;

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
    private android.content.Context themedContext; // 带主题的Context，用于创建Material组件

    // 按钮数据结构
    private static class ButtonData {
        String text;
        String badge;
        String url;

        ButtonData(String text, String badge, String url) {
            this.text = text;
            this.badge = badge;
            this.url = url;
        }
    }

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

            android.util.Log.d("FloatingService", "悬浮图标已初始化");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "初始化图标失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置悬浮图标触摸监听：短按切换菜单，长按拖动
     */
    private void setupIconTouchListener() {
        final int[] lastX = {0};
        final int[] lastY = {0};
        final boolean[] isDragging = {false};
        final long[] downTime = {0};
        final int LONG_PRESS_THRESHOLD = 300; // 长按判定时间(ms)
        final int DRAG_THRESHOLD = 8; // 拖动判定距离(px)

        floatingIcon.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = (int) event.getRawX();
                    lastY[0] = (int) event.getRawY();
                    isDragging[0] = false;
                    downTime[0] = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - lastX[0];
                    int dy = (int) event.getRawY() - lastY[0];
                    // 超过拖动阈值或已在拖动中，则进入拖动模式
                    if (isDragging[0] || Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                        isDragging[0] = true;
                        iconParams.x += dx;
                        iconParams.y += dy;
                        windowManager.updateViewLayout(floatingIcon, iconParams);
                        lastX[0] = (int) event.getRawX();
                        lastY[0] = (int) event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    // 未拖动且按下时间短于长按阈值，视为点击
                    if (!isDragging[0] && (System.currentTimeMillis() - downTime[0]) < LONG_PRESS_THRESHOLD) {
                        toggleMenu();
                    } else if (isDragging[0]) {
                        // 拖动结束后自动贴边
                        snapToEdge();
                    }
                    isDragging[0] = false;
                    return true;
            }
            return false;
        });
    }

    /**
     * 悬浮图标自动贴边
     */
    private void snapToEdge() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int iconWidth = floatingIcon.getWidth();

        // 判断靠左还是靠右
        int centerX = iconParams.x + iconWidth / 2;
        if (centerX < screenWidth / 2) {
            // 贴左边
            iconParams.x = 0;
        } else {
            // 贴右边
            iconParams.x = screenWidth - iconWidth;
        }
        windowManager.updateViewLayout(floatingIcon, iconParams);
    }

    /**
     * 初始化功能菜单
     */
    private void initFloatingMenu() {
        try {
            themedContext = new android.view.ContextThemeWrapper(
                    this, R.style.Theme_Bbzs
            );

            floatingMenu = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_menu, null);

            // 右上角更多菜单
            floatingMenu.findViewById(R.id.iv_menu_more).setOnClickListener(this::showPopupMenu);

            // 添加按钮
            addButtonsToMenu();

            android.util.Log.d("FloatingService", "功能菜单初始化成功");
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "初始化菜单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加所有按钮到菜单
     */
    private void addButtonsToMenu() {
        LinearLayout container = floatingMenu.findViewById(R.id.content_container);
        if (container == null) return;

        // 第一行提示
        addTextRow(container, "部分肥料页每日需进入一次农场激活");

        // 第一组：10W, 6W, 5W, 4W1
        addButtonRow(container, new ButtonData[]{
                new ButtonData("10W", "10次", UrlConstants.URL_10W),
                new ButtonData("6W", "1次", UrlConstants.URL_6W),
                new ButtonData("5W", "10次", UrlConstants.URL_5W),
                new ButtonData("4W1", "1次", UrlConstants.URL_4W1)
        });

        // 第二组：4W2, 3W1, 3W2, 3W3
        addButtonRow(container, new ButtonData[]{
                new ButtonData("4W2", "1次", UrlConstants.URL_4W2),
                new ButtonData("3W1", "10次", UrlConstants.URL_3W1),
                new ButtonData("3W2", "3次", UrlConstants.URL_3W2),
                new ButtonData("3W3", "1次", UrlConstants.URL_3W3)
        });

        // 第三组：3W4, 集汗滴, 乐游记, 大富翁
        addButtonRow(container, new ButtonData[]{
                new ButtonData("3W4", "1次", UrlConstants.URL_3W4),
                new ButtonData("集汗滴", "乐动力", UrlConstants.URL_JIHANDI),
                new ButtonData("乐游记", "乐动力", UrlConstants.URL_LEYOUJI),
                new ButtonData("大富翁", "乐动力", UrlConstants.URL_DAFUWENG)
        });

        // 第四组：限时补贴, 芭芭农场, 肥料明细, 农场兑换
        addButtonRow(container, new ButtonData[]{
                new ButtonData("限时补贴", "乐动力", UrlConstants.URL_XIANSHI_BUTIE),
                new ButtonData("芭芭农场", "", UrlConstants.URL_BABA_FARM),
                new ButtonData("肥料明细", "", UrlConstants.URL_FEILIAO_MINGXI),
                new ButtonData("农场兑换", "", UrlConstants.URL_NONGCHANG_DUIHUAN)
        });

        // 第五组：阳光农场, 代付款, 购物车, 三元三件
        addButtonRow(container, new ButtonData[]{
                new ButtonData("阳光农场", "", UrlConstants.URL_YANGGUANG_FARM),
                new ButtonData("代付款", "", UrlConstants.URL_DAIFUKUAN),
                new ButtonData("购物车", "", UrlConstants.URL_GOUWUCHE),
                new ButtonData("三元三件", "", UrlConstants.URL_SANYUAN_SANJIAN)
        });

        // 第六组：500阳光, 地址管理, 88VIP中心, 一键退会
        addButtonRow(container, new ButtonData[]{
                new ButtonData("500阳光", "", UrlConstants.URL_500_YANGGUANG),
                new ButtonData("地址管理", "", UrlConstants.URL_DIZHI_GUANLI),
                new ButtonData("88VIP中心", "", UrlConstants.URL_88VIP),
                new ButtonData("一键退会", "", UrlConstants.URL_YIJIAN_TUIHUI)
        });

        // 红包区域提示
        addTextRow(container, "红包区域");

        // 第七组：农场秒杀, 秒杀频道, 签到现金, 签到福利
        addButtonRow(container, new ButtonData[]{
                new ButtonData("农场秒杀", "", UrlConstants.URL_NONGCHANG_MIAOSHA),
                new ButtonData("秒杀频道", "", UrlConstants.URL_MIAOSHA_PINDAO),
                new ButtonData("签到现金", "", UrlConstants.URL_QIANDAO_XIANJIN),
                new ButtonData("签到福利", "", UrlConstants.URL_QIANDAO_FULI)
        });

        // 第八组：省钱购, 福利购, 有好券, U选好价
        addButtonRow(container, new ButtonData[]{
                new ButtonData("省钱购", "", UrlConstants.URL_SHENGQIAN_GOU),
                new ButtonData("福利购", "", UrlConstants.URL_FULI_GOU),
                new ButtonData("有好券", "", UrlConstants.URL_YOUHAO_QUAN),
                new ButtonData("U选好价", "", UrlConstants.URL_UXUAN_HAOJIA)
        });

        // 第九组：喜从天降, 拍拍乐, 淘金币, 淘出666
        addButtonRow(container, new ButtonData[]{
                new ButtonData("喜从天降", "", UrlConstants.URL_XICONG_TIANJIAN),
                new ButtonData("拍拍乐", "", UrlConstants.URL_PAIPAILE),
                new ButtonData("淘金币", "", UrlConstants.URL_TAOJINBI),
                new ButtonData("淘出666", "", UrlConstants.URL_TAOCHU_666)
        });

        // 第十组：淘宝密码, 百亿补贴, 淘宝成就, 天天砸金蛋
        addButtonRow(container, new ButtonData[]{
                new ButtonData("淘宝密码", "", UrlConstants.URL_TAOBAO_MIMA),
                new ButtonData("百亿补贴", "", UrlConstants.URL_BAIYI_BUTIE),
                new ButtonData("淘宝成就", "", UrlConstants.URL_TAOBAO_CHENGJIU),
                new ButtonData("天天砸金蛋", "", UrlConstants.URL_TIANTIAN_ZAJINDANG)
        });
    }

    /**
     * 添加文本行
     */
    private void addTextRow(LinearLayout container, String text) {
        android.widget.TextView tv = new android.widget.TextView(themedContext);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(0xFFFF6200);
        tv.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 8;
        params.bottomMargin = 4;
        tv.setLayoutParams(params);
        container.addView(tv);
    }

    /**
     * 添加按钮行
     */
    private void addButtonRow(LinearLayout container, ButtonData[] buttons) {
        LinearLayout row = new LinearLayout(themedContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = 2;
        rowParams.bottomMargin = 2;
        row.setLayoutParams(rowParams);

        for (int i = 0; i < buttons.length; i++) {
            addButton(row, buttons[i], i < buttons.length - 1);
        }

        container.addView(row);
    }

    /**
     * 添加单个按钮
     */
    private void addButton(LinearLayout row, ButtonData data, boolean hasMargin) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(themedContext);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        if (hasMargin) {
            frameParams.rightMargin = 2;
        }
        frame.setLayoutParams(frameParams);

        MaterialButton btn = new MaterialButton(themedContext);
        btn.setText(data.text);
        btn.setTextSize(11);
        btn.setMinHeight(40);
        btn.setCornerRadius(8);
        btn.setStrokeWidth(1);
        btn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFDDDDDD));
        btn.setBackgroundColor(0xFFFFFFFF);
        btn.setTextColor(0xFF333333);
        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(btnParams);

        final String url = data.url;
        btn.setOnClickListener(v -> openTaobaoUrl(url));

        frame.addView(btn);

        // 添加badge
        if (data.badge != null && !data.badge.isEmpty()) {
            android.widget.TextView badge = new android.widget.TextView(themedContext);
            badge.setText(data.badge);
            badge.setTextSize(8);
            badge.setTextColor(0xFFFF6200);
            badge.setBackgroundResource(R.drawable.bg_badge);
            badge.setPadding(2, 1, 2, 1);
            android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END | android.view.Gravity.TOP
            );
            badgeParams.topMargin = 1;
            badgeParams.rightMargin = 2;
            badge.setLayoutParams(badgeParams);
            frame.addView(badge);
        }

        row.addView(frame);
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
            if (floatingMenu == null) {
                initFloatingMenu();
            }

            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            // 获取屏幕尺寸，菜单高度 = 屏幕高度 - 上下各60px边距
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            int margin = 60; // px
            int menuWidth = screenWidth - margin * 2;
            int menuHeight = screenHeight - margin * 2;

            menuParams = new WindowManager.LayoutParams(
                    menuWidth,
                    menuHeight,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.CENTER;

            windowManager.addView(floatingMenu, menuParams);
            isMenuVisible = true;

            // 设置外部点击隐藏
            floatingMenu.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    hideMenu();
                    return true;
                }
                return false;
            });

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
                    Toast.makeText(this, "芭芭助手 v1.01", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
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
            intent.setPackage("com.taobao.taobao");
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * 创建通知渠道
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
                .setContentTitle("芭芭助手")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(R.drawable.ic_floating)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
