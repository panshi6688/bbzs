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
import android.util.Log;
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
    private static final String PREF_NAME = "bbzs_settings";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final float DEFAULT_FONT_SCALE = 1.0f; // 默认字体缩放比例

    private WindowManager windowManager;
    private View floatingIcon;
    private View floatingMenu;
    private View overlayMask; // 全屏透明遮罩层，拦截菜单外部点击
    private View fontSizePanel; // 字体大小调整面板
    private WindowManager.LayoutParams iconParams;
    private WindowManager.LayoutParams menuParams;
    private boolean isMenuVisible = false;
    private boolean isFontPanelVisible = false;
    private android.content.Context themedContext; // 带主题的Context，用于创建Material组件
    private float currentFontScale = DEFAULT_FONT_SCALE; // 当前字体缩放比例

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
            // 加载保存的字体缩放比例
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            currentFontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE);

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
        if (fontSizePanel != null && fontSizePanel.isAttachedToWindow()) {
            windowManager.removeView(fontSizePanel);
        }
        if (floatingIcon != null) windowManager.removeView(floatingIcon);
        if (floatingMenu != null && floatingMenu.isAttachedToWindow()) {
            windowManager.removeView(floatingMenu);
        }
        if (overlayMask != null && overlayMask.isAttachedToWindow()) {
            windowManager.removeView(overlayMask);
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

            // 获取屏幕尺寸，设置默认位置为屏幕右边中间
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            int iconSize = (int) (48 * dm.density); // 48dp转px

            iconParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            iconParams.gravity = Gravity.TOP | Gravity.START;
            iconParams.x = screenWidth - iconSize; // 右边
            iconParams.y = (screenHeight - iconSize) / 2; // 垂直居中

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
        tv.setTextSize(14 * currentFontScale); // 应用字体缩放
        tv.setTextColor(0xFFFF6200);
        tv.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 8;
        params.bottomMargin = 4;
        tv.setLayoutParams(params);
        tv.setTag("text_row"); // 标记用于后续更新
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
        rowParams.topMargin = 1;
        rowParams.bottomMargin = 1;
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

        boolean hasBadge = data.badge != null && !data.badge.isEmpty();

        // 使用OutlinedButton样式，白色背景
        MaterialButton btn = new MaterialButton(themedContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(data.text);
        btn.setTextSize(13 * currentFontScale); // 应用字体缩放
        btn.setMinHeight(44);
        btn.setCornerRadius(8);
        btn.setStrokeWidth(1);
        btn.setStrokeColorResource(android.R.color.darker_gray);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        btn.setTextColor(0xFF333333);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setPadding(4, 4, 4, 4);
        btn.setTag("btn_text"); // 标记用于后续更新

        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(btnParams);

        final String url = data.url;
        btn.setOnClickListener(v -> openTaobaoUrl(url));
        frame.addView(btn);

        // badge在按钮内部右上角显示
        if (hasBadge) {
            android.widget.TextView badge = new android.widget.TextView(themedContext);
            badge.setText(data.badge);
            badge.setTextSize(9 * currentFontScale); // 应用字体缩放
            badge.setTextColor(0xFFFF6200);
            android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END | android.view.Gravity.TOP
            );
            // 设置足够的margin确保在按钮圆角内部显示
            badgeParams.topMargin = 6;
            badgeParams.rightMargin = 8;
            badge.setLayoutParams(badgeParams);
            badge.setTag("badge_text"); // 标记用于后续更新
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

            // 获取屏幕尺寸
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            int horizontalMargin = 120;
            int verticalMargin = 200;
            int menuWidth = screenWidth - horizontalMargin * 2;

            // 先测量菜单内容实际高度
            floatingMenu.measure(
                    View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int contentHeight = floatingMenu.getMeasuredHeight();
            int maxHeight = screenHeight - verticalMargin * 2;
            int menuHeight = Math.min(contentHeight, maxHeight);

            // 1. 先添加全屏透明遮罩层，拦截外部点击
            overlayMask = new View(this);
            overlayMask.setBackgroundColor(0x01000000); // 几乎全透明，但能拦截触摸
            WindowManager.LayoutParams maskParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            overlayMask.setOnClickListener(v -> hideMenu());
            windowManager.addView(overlayMask, maskParams);

            // 2. 再添加菜单面板
            menuParams = new WindowManager.LayoutParams(
                    menuWidth,
                    menuHeight,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.CENTER;

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
            if (overlayMask != null && overlayMask.isAttachedToWindow()) {
                windowManager.removeView(overlayMask);
                overlayMask = null;
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
                } else if (id == R.id.menu_font_size) {
                    showFontSizePanel();
                    return true;
                } else if (id == R.id.menu_help) {
                    showHelpDialog();
                    return true;
                } else if (id == R.id.menu_about) {
                    showAboutDialog();
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
     * 显示使用帮助对话框
     */
    private void showHelpDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("使用帮助")
                .setMessage(
                        "1. 点击悬浮图标打开功能菜单\n" +
                        "2. 拖动悬浮图标可移动位置，松手自动贴边\n" +
                        "3. 点击功能按钮跳转到淘宝对应页面\n" +
                        "4. 部分肥料页需每日进入一次农场激活\n" +
                        "5. 点击菜单外部区域关闭菜单")
                .setPositiveButton("知道了", null);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    /**
     * 显示关于对话框
     */
    private void showAboutDialog() {
        String versionName = "1.0.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            // 忽略
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("关于")
                .setMessage("芭芭助手 v" + versionName + "\n\n" +
                        "淘宝农场助手工具\n" +
                        "快速访问常用功能页面")
                .setPositiveButton("确定", null);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    /**
     * 打开淘宝链接
     */
    private void openTaobaoUrl(String url) {
        // URL有效性验证
        if (url == null || url.trim().isEmpty()) {
            Log.e("FloatingService", "URL is null or empty");
            Toast.makeText(this, "链接地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // URL格式验证
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.e("FloatingService", "Invalid URL format: " + url);
            Toast.makeText(this, "链接格式无效", Toast.LENGTH_SHORT).show();
            return;
        }

        

        Log.d("FloatingService", "Opening URL: " + url);

        try {
            Intent intent;
            
            // 对s.m.taobao.com和web.m.taobao.com使用淘宝的taobao:// scheme
            if (url.contains("s.m.taobao.com") || url.contains("web.m.taobao.com")) {
                // 将https://转换为taobao://
                String taobaoSchemeUrl = url.replace("https://", "taobao://")
                                           .replace("http://", "taobao://");
                Log.d("FloatingService", "Using Taobao scheme: " + taobaoSchemeUrl);
                
                try {
                    intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(taobaoSchemeUrl));
                    intent.setPackage("com.taobao.taobao");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    Log.d("FloatingService", "URL opened with Taobao scheme");
                    return;
                } catch (Exception e) {
                    Log.e("FloatingService", "Failed to open with Taobao scheme, fallback to normal intent", e);
                    // 如果taobao://失败，继续使用普通方式
                }
            }
            
            // 对其他淘宝/天猫链接使用标准方式
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (url.contains(".taobao.com") || url.contains(".tmall.com")) {
                Log.d("FloatingService", "Opening with Taobao app package");
                intent.setPackage("com.taobao.taobao");
            } else {
                Log.d("FloatingService", "Using system default handler");
            }
            
            startActivity(intent);
            Log.d("FloatingService", "URL opened successfully");
        } catch (Exception e) {
            Log.e("FloatingService", "Failed to open URL: " + url, e);
            // 最后的兜底方案：不指定package让系统选择
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                Log.d("FloatingService", "URL opened with system handler");
            } catch (Exception ex) {
                Log.e("FloatingService", "All methods failed: " + url, ex);
                Toast.makeText(this, "打开链接失败，请检查是否安装淘宝", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 显示字体大小调整面板
     */
    private void showFontSizePanel() {
        if (isFontPanelVisible) return;

        try {
            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            // 获取屏幕尺寸
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int horizontalMargin = 120;
            int panelWidth = screenWidth - horizontalMargin * 2;

            // 创建面板布局
            LinearLayout panel = new LinearLayout(themedContext);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundColor(0xFFFFFFFF);
            panel.setPadding(20, 20, 20, 20);

            // 标题
            android.widget.TextView title = new android.widget.TextView(themedContext);
            title.setText("调整字体大小");
            title.setTextSize(16);
            title.setTextColor(0xFF333333);
            title.setGravity(Gravity.CENTER);
            panel.addView(title);

            // 当前字体大小显示
            android.widget.TextView sizeText = new android.widget.TextView(themedContext);
            sizeText.setText(String.format("当前: %.1f倍", currentFontScale));
            sizeText.setTextSize(14);
            sizeText.setTextColor(0xFF666666);
            sizeText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            sizeParams.topMargin = 10;
            sizeText.setLayoutParams(sizeParams);
            panel.addView(sizeText);

            // 滑动条容器
            LinearLayout sliderContainer = new LinearLayout(themedContext);
            sliderContainer.setOrientation(LinearLayout.HORIZONTAL);
            sliderContainer.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams sliderContainerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            sliderContainerParams.topMargin = 20;
            sliderContainer.setLayoutParams(sliderContainerParams);

            // 减小按钮
            MaterialButton btnDecrease = new MaterialButton(themedContext);
            btnDecrease.setText("-");
            btnDecrease.setTextSize(18);
            LinearLayout.LayoutParams decreaseParams = new LinearLayout.LayoutParams(
                    60,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnDecrease.setLayoutParams(decreaseParams);

            // 滑动条
            android.widget.SeekBar seekBar = new android.widget.SeekBar(themedContext);
            seekBar.setMax(20); // 0.5倍到2.5倍，步长0.1
            seekBar.setProgress((int)((currentFontScale - 0.5f) * 10));
            LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            seekBarParams.leftMargin = 10;
            seekBarParams.rightMargin = 10;
            seekBar.setLayoutParams(seekBarParams);

            // 增大按钮
            MaterialButton btnIncrease = new MaterialButton(themedContext);
            btnIncrease.setText("+");
            btnIncrease.setTextSize(18);
            LinearLayout.LayoutParams increaseParams = new LinearLayout.LayoutParams(
                    60,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnIncrease.setLayoutParams(increaseParams);

            sliderContainer.addView(btnDecrease);
            sliderContainer.addView(seekBar);
            sliderContainer.addView(btnIncrease);
            panel.addView(sliderContainer);

            // 按钮容器
            LinearLayout buttonContainer = new LinearLayout(themedContext);
            buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
            buttonContainer.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams buttonContainerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            buttonContainerParams.topMargin = 20;
            buttonContainer.setLayoutParams(buttonContainerParams);

            // 确定按钮
            MaterialButton btnConfirm = new MaterialButton(themedContext);
            btnConfirm.setText("确定");
            LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            confirmParams.rightMargin = 10;
            btnConfirm.setLayoutParams(confirmParams);

            // 取消按钮
            MaterialButton btnCancel = new MaterialButton(themedContext);
            btnCancel.setText("取消");
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            cancelParams.leftMargin = 10;
            btnCancel.setLayoutParams(cancelParams);

            buttonContainer.addView(btnConfirm);
            buttonContainer.addView(btnCancel);
            panel.addView(buttonContainer);

            // 滑动条监听
            seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    float newScale = 0.5f + progress * 0.1f;
                    sizeText.setText(String.format("当前: %.1f倍", newScale));
                    currentFontScale = newScale;
                    updateMenuFontSize();
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            });

            // 减小按钮监听
            btnDecrease.setOnClickListener(v -> {
                int progress = seekBar.getProgress();
                if (progress > 0) {
                    seekBar.setProgress(progress - 1);
                    float newScale = 0.5f + (progress - 1) * 0.1f;
                    sizeText.setText(String.format("当前: %.1f倍", newScale));
                    currentFontScale = newScale;
                    updateMenuFontSize();
                }
            });

            // 增大按钮监听
            btnIncrease.setOnClickListener(v -> {
                int progress = seekBar.getProgress();
                if (progress < 20) {
                    seekBar.setProgress(progress + 1);
                    float newScale = 0.5f + (progress + 1) * 0.1f;
                    sizeText.setText(String.format("当前: %.1f倍", newScale));
                    currentFontScale = newScale;
                    updateMenuFontSize();
                }
            });

            // 确定按钮监听
            btnConfirm.setOnClickListener(v -> {
                saveFontScale();
                hideFontSizePanel();
                Toast.makeText(this, "字体大小已保存", Toast.LENGTH_SHORT).show();
            });

            // 取消按钮监听
            btnCancel.setOnClickListener(v -> {
                // 恢复原来的字体大小
                android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                currentFontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE);
                updateMenuFontSize();
                hideFontSizePanel();
            });

            fontSizePanel = panel;

            // 添加到窗口
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    panelWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(fontSizePanel, params);
            isFontPanelVisible = true;

        } catch (Exception e) {
            android.util.Log.e("FloatingService", "显示字体调整面板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 隐藏字体大小调整面板
     */
    private void hideFontSizePanel() {
        if (!isFontPanelVisible) return;
        try {
            if (fontSizePanel != null && fontSizePanel.isAttachedToWindow()) {
                windowManager.removeView(fontSizePanel);
                fontSizePanel = null;
            }
            isFontPanelVisible = false;
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "隐藏字体调整面板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新菜单字体大小
     */
    private void updateMenuFontSize() {
        if (floatingMenu == null) return;

        LinearLayout container = floatingMenu.findViewById(R.id.content_container);
        if (container == null) return;

        // 遍历所有子View更新字体大小
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);

            // 更新文本行
            if (child instanceof android.widget.TextView && "text_row".equals(child.getTag())) {
                ((android.widget.TextView) child).setTextSize(14 * currentFontScale);
            }

            // 更新按钮行
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View frameView = row.getChildAt(j);
                    if (frameView instanceof android.widget.FrameLayout) {
                        android.widget.FrameLayout frame = (android.widget.FrameLayout) frameView;
                        for (int k = 0; k < frame.getChildCount(); k++) {
                            View btnView = frame.getChildAt(k);
                            // 更新按钮文字
                            if (btnView instanceof MaterialButton && "btn_text".equals(btnView.getTag())) {
                                ((MaterialButton) btnView).setTextSize(13 * currentFontScale);
                            }
                            // 更新badge文字
                            if (btnView instanceof android.widget.TextView && "badge_text".equals(btnView.getTag())) {
                                ((android.widget.TextView) btnView).setTextSize(9 * currentFontScale);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 保存字体缩放比例
     */
    private void saveFontScale() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putFloat(KEY_FONT_SCALE, currentFontScale).apply();
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
