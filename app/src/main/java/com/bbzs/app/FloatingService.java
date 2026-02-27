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
import com.google.android.material.tabs.TabLayout;

/**
 * 悬浮窗服务：管理悬浮图标和功能菜单
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "bbzs_floating_channel";
    private static final String PREF_NAME = "bbzs_settings";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_QUICK_ACCESS = "quick_access_";
    private static final String KEY_SEARCH_KEYWORDS = "search_keywords"; // E卡搜索关键词历史
    private static final String KEY_MENU_ALPHA = "menu_alpha"; // 菜单透明度
    private static final float DEFAULT_FONT_SCALE = 1.0f; // 默认字体缩放比例
    private static final float DEFAULT_MENU_ALPHA = 1.0f; // 默认菜单不透明度（0%透明，完全不透明）
    private static final int MAX_QUICK_ACCESS = 4; // 快速访问位置数量

    private WindowManager windowManager;
    private View floatingIcon;
    private View floatingMenu;
    private View fontSizePanel; // 字体大小调整面板
    private View alphaPanel; // 透明度调整面板
    private WindowManager.LayoutParams iconParams;
    private WindowManager.LayoutParams menuParams;
    private boolean isMenuVisible = false;
    private boolean isFontPanelVisible = false;
    private boolean isAlphaPanelVisible = false;
    private android.content.Context themedContext; // 带主题的Context，用于创建Material组件
    private float currentFontScale = DEFAULT_FONT_SCALE; // 当前字体缩放比例
    private float currentMenuAlpha = DEFAULT_MENU_ALPHA; // 当前菜单不透明度
    
    // 标签页相关
    private TabLayout tabLayout;
    private LinearLayout contentContainer;
    private int currentTabIndex = 0; // 当前选中的标签索引
    
    // 快速访问相关
    private LinearLayout quickAccessContainer;
    private java.util.List<ButtonData> quickAccessButtons = new java.util.ArrayList<>();
    
    // E卡搜索相关
    private android.widget.AutoCompleteTextView searchKeywordInput;
    private android.widget.ArrayAdapter<String> keywordAdapter;
    private java.util.List<String> searchKeywords = new java.util.ArrayList<>();

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
            // 加载保存的设置
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            currentFontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE);
            currentMenuAlpha = prefs.getFloat(KEY_MENU_ALPHA, DEFAULT_MENU_ALPHA);

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
        if (alphaPanel != null && alphaPanel.isAttachedToWindow()) {
            windowManager.removeView(alphaPanel);
        }
        if (fontSizePanel != null && fontSizePanel.isAttachedToWindow()) {
            windowManager.removeView(fontSizePanel);
        }
        if (floatingIcon != null && floatingIcon.isAttachedToWindow()) {
            windowManager.removeView(floatingIcon);
        }
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
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
     * 设置悬浮图标触摸监听:短按切换菜单,长按拖动
     */
    private void setupIconTouchListener() {
        final int[] lastX = {0};
        final int[] lastY = {0};
        final int[] initialX = {0};
        final int[] initialY = {0};
        final boolean[] isDragging = {false};
        final long[] downTime = {0};
        final int CLICK_THRESHOLD = 200; // 点击判定时间(ms)
        final int DRAG_THRESHOLD = 15; // 拖动判定距离(px),增大阈值避免误判

        floatingIcon.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = (int) event.getRawX();
                    lastY[0] = (int) event.getRawY();
                    initialX[0] = lastX[0];
                    initialY[0] = lastY[0];
                    isDragging[0] = false;
                    downTime[0] = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - lastX[0];
                    int dy = (int) event.getRawY() - lastY[0];
                    int totalDx = Math.abs((int) event.getRawX() - initialX[0]);
                    int totalDy = Math.abs((int) event.getRawY() - initialY[0]);
                    
                    // 只有移动距离超过阈值才进入拖动模式
                    if (!isDragging[0] && (totalDx > DRAG_THRESHOLD || totalDy > DRAG_THRESHOLD)) {
                        isDragging[0] = true;
                    }
                    
                    if (isDragging[0]) {
                        iconParams.x += dx;
                        iconParams.y += dy;
                        windowManager.updateViewLayout(floatingIcon, iconParams);
                        lastX[0] = (int) event.getRawX();
                        lastY[0] = (int) event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    long pressDuration = System.currentTimeMillis() - downTime[0];
                    int totalMove = Math.abs((int) event.getRawX() - initialX[0]) + 
                                   Math.abs((int) event.getRawY() - initialY[0]);
                    
                    // 判断为点击:时间短且移动距离小
                    if (!isDragging[0] && pressDuration < CLICK_THRESHOLD && totalMove < DRAG_THRESHOLD) {
                        v.performClick();
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

            // 点击标题栏关闭菜单
            View titleBar = floatingMenu.findViewById(R.id.title_bar);
            if (titleBar != null) {
                titleBar.setOnClickListener(v -> hideMenu());
            }

            // 初始化TabLayout
            tabLayout = floatingMenu.findViewById(R.id.tab_layout);
            contentContainer = floatingMenu.findViewById(R.id.content_container);
            quickAccessContainer = floatingMenu.findViewById(R.id.quick_access_container);
            
            // 添加标签
            tabLayout.addTab(tabLayout.newTab().setText("全部\n功能"));
            tabLayout.addTab(tabLayout.newTab().setText("三元\n三件"));
            tabLayout.addTab(tabLayout.newTab().setText("兑换\n过肥"));
            tabLayout.addTab(tabLayout.newTab().setText("E卡\n猫超"));
            tabLayout.addTab(tabLayout.newTab().setText("地址\n生成"));
            tabLayout.addTab(tabLayout.newTab().setText("查违\n禁店"));
            
            // 设置标签模式为可滚动，以适应更多标签
            tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
            
            // 设置标签文本居中和多行显示
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && tab.view != null) {
                    android.widget.TextView textView = (android.widget.TextView) tab.view.findViewById(com.google.android.material.R.id.text);
                    if (textView != null) {
                        textView.setGravity(android.view.Gravity.CENTER);
                        textView.setMaxLines(2);
                        textView.setSingleLine(false);
                    }
                }
            }
            
            // 标签切换监听
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    currentTabIndex = tab.getPosition();
                    updateContentForTab(currentTabIndex);
                    // 切换标签后刷新菜单高度
                    refreshMenuHeight();
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });

            // 加载快速访问按钮
            loadQuickAccessButtons();
            
            // 初始化快速访问区域
            updateQuickAccessView();
            
            // 添加按钮到"全部功能"标签
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
        if (contentContainer == null) return;
        contentContainer.removeAllViews(); // 清空现有内容

        // 第一行提示
        addTextRow(contentContainer, "部分肥料页每日需进入一次农场激活");

        // 第一组：10W, 6W, 5W, 4W1
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("10W", "10次", UrlConstants.URL_10W),
                new ButtonData("6W", "1次", UrlConstants.URL_6W),
                new ButtonData("5W", "10次", UrlConstants.URL_5W),
                new ButtonData("4W①", "1次", UrlConstants.URL_4W1)
        });

        // 第二组：4W2, 3W1, 3W2, 3W3
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("4W②", "1次", UrlConstants.URL_4W2),
                new ButtonData("3W①", "10次", UrlConstants.URL_3W1),
                new ButtonData("3W②", "3次", UrlConstants.URL_3W2),
                new ButtonData("3W③", "1次", UrlConstants.URL_3W3)
        });

        // 第三组：3W4, 集汗滴, 乐游记, 大富翁
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("3W④", "1次", UrlConstants.URL_3W4),
                new ButtonData("集汗滴", "乐动力", UrlConstants.URL_JIHANDI),
                new ButtonData("乐游记", "乐动力", UrlConstants.URL_LEYOUJI),
                new ButtonData("大富翁", "乐动力", UrlConstants.URL_DAFUWENG)
        });

        // 第四组：限时补贴, 芭芭农场, 肥料明细, 农场兑换
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("限时补贴", "乐动力", UrlConstants.URL_XIANSHI_BUTIE),
                new ButtonData("芭芭农场", "", UrlConstants.URL_BABA_FARM),
                new ButtonData("肥料明细", "", UrlConstants.URL_FEILIAO_MINGXI),
                new ButtonData("农场兑换", "", UrlConstants.URL_NONGCHANG_DUIHUAN)
        });

        // 第五组：阳光农场, 代付款, 购物车, 三元三件
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("阳光农场", "", UrlConstants.URL_YANGGUANG_FARM),
                new ButtonData("代付款", "", UrlConstants.URL_DAIFUKUAN),
                new ButtonData("购物车", "", UrlConstants.URL_GOUWUCHE),
                new ButtonData("三元三件", "", UrlConstants.URL_SANYUAN_SANJIAN)
        });

        // 第六组：500阳光, 地址管理, 88VIP中心, 一键退会
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("500阳光", "", UrlConstants.URL_500_YANGGUANG),
                new ButtonData("地址管理", "", UrlConstants.URL_DIZHI_GUANLI),
                new ButtonData("88VIP中心", "", UrlConstants.URL_88VIP),
                new ButtonData("一键退会", "", UrlConstants.URL_YIJIAN_TUIHUI)
        });

        // 红包区域提示
        addTextRow(contentContainer, "红包区域");

        // 第七组：农场秒杀, 秒杀频道, 签到现金, 签到福利
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("农场秒杀", "", UrlConstants.URL_NONGCHANG_MIAOSHA),
                new ButtonData("秒杀频道", "", UrlConstants.URL_MIAOSHA_PINDAO),
                new ButtonData("签到现金", "", UrlConstants.URL_QIANDAO_XIANJIN),
                new ButtonData("签到福利", "", UrlConstants.URL_QIANDAO_FULI)
        });

        // 第八组：省钱购, 福利购, 有好券, U选好价
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("省钱购", "", UrlConstants.URL_SHENGQIAN_GOU),
                new ButtonData("福利购", "", UrlConstants.URL_FULI_GOU),
                new ButtonData("有好券", "", UrlConstants.URL_YOUHAO_QUAN),
                new ButtonData("U选好价", "", UrlConstants.URL_UXUAN_HAOJIA)
        });

        // 第九组：喜从天降, 拍拍乐, 淘金币, 淘出666
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("喜从天降", "", UrlConstants.URL_XICONG_TIANJIAN),
                new ButtonData("拍拍乐", "", UrlConstants.URL_PAIPAILE),
                new ButtonData("淘金币", "", UrlConstants.URL_TAOJINBI),
                new ButtonData("淘出666", "", UrlConstants.URL_TAOCHU_666)
        });

        // 第十组：淘宝密码, 百亿补贴, 淘宝成就, 天天砸金蛋
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("淘宝密码", "", UrlConstants.URL_TAOBAO_MIMA),
                new ButtonData("百亿补贴", "", UrlConstants.URL_BAIYI_BUTIE),
                new ButtonData("淘宝成就", "", UrlConstants.URL_TAOBAO_CHENGJIU),
                new ButtonData("天天砸金蛋", "", UrlConstants.URL_TIANTIAN_ZAJINDANG)
        });

        // 点淘区域提示
        addTextRow(contentContainer, "点淘区域");

        // 第十一组：店铺主页, 购物车, 代付款
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("店铺主页", "点淘", UrlConstants.URL_DIANTAO_SHOP),
                new ButtonData("购物车", "点淘", UrlConstants.URL_DIANTAO_CART),
                new ButtonData("代付款", "点淘", UrlConstants.URL_DIANTAO_DAIFUKUAN),
                null // 空位
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
            if (buttons[i] != null) {
                addButton(row, buttons[i], i < buttons.length - 1);
            } else {
                // 添加空占位
                addEmptySpace(row, i < buttons.length - 1);
            }
        }

        container.addView(row);
    }

    /**
     * 添加空占位
     */
    private void addEmptySpace(LinearLayout row, boolean hasMargin) {
        View space = new View(themedContext);
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        if (hasMargin) {
            spaceParams.rightMargin = 2;
        }
        space.setLayoutParams(spaceParams);
        row.addView(space);
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
        btn.setOnClickListener(v -> {
            openTaobaoUrl(url);
            hideMenu(); // 点击按钮后隐藏菜单
        });
        
        // 长按添加到快速访问
        btn.setOnLongClickListener(v -> {
            showQuickAccessDialog(data);
            return true;
        });
        
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

            // 添加菜单面板
            menuParams = new WindowManager.LayoutParams(
                    menuWidth,
                    menuHeight,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.CENTER;
            menuParams.alpha = currentMenuAlpha; // 应用透明度
            menuParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | 
                                       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING; // 输入法不影响窗口

            // 监听外部点击事件
            floatingMenu.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
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
            android.view.ContextThemeWrapper wrapper = new android.view.ContextThemeWrapper(this, R.style.Theme_Bbzs);
            PopupMenu popup = new PopupMenu(wrapper, anchor);
            popup.getMenuInflater().inflate(R.menu.menu_more, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_exit) {
                    stopSelf();
                    return true;
                } else if (id == R.id.menu_alpha) {
                    showAlphaPanel();
                    return true;
                } else if (id == R.id.menu_font_size) {
                    showFontSizePanel();
                    return true;
                } else if (id == R.id.menu_qq_group) {
                    openQQGroup();
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
                        "5. 点击界面标题栏可以关掉界面显示\n" +
                        "6. 点击菜单外部区域也可关闭菜单")
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
                        "快速访问常用功能页面\n"+
                        "软件更新请加入QQ群查看")
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
            // 点淘相关链接特殊处理
            if (url.equals(UrlConstants.URL_DIANTAO_SHOP) || 
                url.equals(UrlConstants.URL_DIANTAO_CART) || 
                url.equals(UrlConstants.URL_DIANTAO_DAIFUKUAN)) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.taobao.live");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                Log.d("FloatingService", "Opened with Diantao app");
                return;
            }
            
            // 对s.m.taobao.com和web.m.taobao.com使用scheme直接跳转（优化后）
            if (url.contains("s.m.taobao.com") || url.contains("web.m.taobao.com")) {
                Log.d("FloatingService", "Using tbopen scheme for direct jump");
                openWithTbOpenScheme(url);
                return;
            }
            
            // 对其他淘宝/天猫链接，直接使用淘宝app打开
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (url.contains(".taobao.com") || url.contains(".tmall.com")) {
                // 直接设置package为淘宝，不再使用queryIntentActivities
                // 因为Android 11+的包可见性限制可能导致查询不到淘宝app
                intent.setPackage("com.taobao.taobao");
                Log.d("FloatingService", "Opening Taobao/Tmall URL with Taobao app");
            } else {
                Log.d("FloatingService", "Using system default handler");
            }
            
            startActivity(intent);
            Log.d("FloatingService", "URL opened successfully");
        } catch (Exception e) {
            Log.e("FloatingService", "Failed to open URL: " + url, e);
            Toast.makeText(this, "打开链接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 使用tbopen scheme直接打开淘宝页面（优化方案，跳过WebView）
     */
    private void openWithTbOpenScheme(String url) {
        try {
            // 构造tbopen scheme
            String encodedUrl = java.net.URLEncoder.encode(url, "UTF-8");
            String scheme = "tbopen://m.taobao.com/tbopen/index.html?" +
                    "h5Url=" + encodedUrl +
                    "&action=ali.open.nav" +
                    "&module=h5" +
                    "&bootImage=0" +
                    "&slk_t=" + System.currentTimeMillis() +
                    "&slk_gid=gid_er_sidebar_0" +
                    "&afcPromotionOpen=false" +
                    "&bc_fl_src=h5_huanduan" +
                    "&source=slk_dp";
            
            Log.d("FloatingService", "Constructed tbopen scheme: " + scheme);
            
            // 使用scheme打开淘宝
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme));
            intent.setPackage("com.taobao.taobao");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            Log.d("FloatingService", "Opened with tbopen scheme successfully");
            
        } catch (Exception e) {
            Log.e("FloatingService", "Failed to open with tbopen scheme, fallback to WebView", e);
            // 如果scheme方式失败，降级到WebView方式
            openUrlWithWebView(url);
        }
    }

    /**
     * 使用WebView加载URL，让页面的JavaScript处理跳转到淘宝app（降级方案）
     */
    private void openUrlWithWebView(final String url) {
        try {
            // 创建一个隐藏的WebView
            final android.webkit.WebView webView = new android.webkit.WebView(this);
            
            // 启用JavaScript
            android.webkit.WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            
            // 设置WebViewClient来拦截URL加载
            webView.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String interceptedUrl) {
                    Log.d("FloatingService", "WebView intercepted URL: " + interceptedUrl);
                    
                    // 如果是淘宝scheme，尝试打开淘宝app
                    if (interceptedUrl.startsWith("taobao://") || 
                        interceptedUrl.startsWith("tbopen://")) {
                        
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(interceptedUrl));
                            intent.setPackage("com.taobao.taobao");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Log.d("FloatingService", "Opened Taobao app with scheme: " + interceptedUrl);
                            
                            // 清理WebView
                            webView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    webView.destroy();
                                }
                            }, 1000);
                            
                            return true;
                        } catch (Exception e) {
                            Log.e("FloatingService", "Failed to open Taobao scheme: " + interceptedUrl, e);
                        }
                    }
                    
                    // 继续在WebView中加载
                    return false;
                }
                
                @Override
                public void onPageFinished(android.webkit.WebView view, String finishedUrl) {
                    super.onPageFinished(view, finishedUrl);
                    Log.d("FloatingService", "WebView page finished: " + finishedUrl);
                    
                    // 如果页面加载完成后还在原URL，说明没有跳转，可能需要用户交互
                    // 这种情况下，我们等待一段时间看是否有scheme跳转
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 如果5秒后还没有跳转，清理WebView
                            // 用户可能需要在浏览器中手动点击"打开淘宝"
                            Log.d("FloatingService", "WebView timeout, may need user interaction");
                        }
                    }, 5000);
                }
                
                @Override
                public void onReceivedError(android.webkit.WebView view, int errorCode, 
                                          String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e("FloatingService", "WebView error: " + description);
                }
            });
            
            // 加载URL
            Log.d("FloatingService", "WebView loading URL: " + url);
            webView.loadUrl(url);
            
        } catch (Exception e) {
            Log.e("FloatingService", "Failed to create WebView: " + e.getMessage(), e);
            Toast.makeText(this, "打开链接失败", Toast.LENGTH_SHORT).show();
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
     * 显示透明度调整面板
     */
    private void showAlphaPanel() {
        if (isAlphaPanelVisible) return;

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
            title.setText("调整透明度");
            title.setTextSize(16);
            title.setTextColor(0xFF333333);
            title.setGravity(Gravity.CENTER);
            panel.addView(title);

            // 当前透明度显示
            android.widget.TextView alphaText = new android.widget.TextView(themedContext);
            int alphaPercent = (int)((1 - currentMenuAlpha) * 100);
            alphaText.setText(String.format("当前透明度: %d%%", alphaPercent));
            alphaText.setTextSize(14);
            alphaText.setTextColor(0xFF666666);
            alphaText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams alphaTextParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            alphaTextParams.topMargin = 10;
            alphaText.setLayoutParams(alphaTextParams);
            panel.addView(alphaText);

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

            // 滑动条 (0-100, 表示透明度百分比)
            android.widget.SeekBar seekBar = new android.widget.SeekBar(themedContext);
            seekBar.setMax(100);
            seekBar.setProgress(alphaPercent);
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
                    float newAlpha = 1.0f - (progress / 100.0f); // 转换为不透明度
                    alphaText.setText(String.format("当前透明度: %d%%", progress));
                    currentMenuAlpha = newAlpha;
                    updateMenuAlpha();
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
                    seekBar.setProgress(progress - 5);
                }
            });

            // 增大按钮监听
            btnIncrease.setOnClickListener(v -> {
                int progress = seekBar.getProgress();
                if (progress < 100) {
                    seekBar.setProgress(progress + 5);
                }
            });

            // 确定按钮监听
            btnConfirm.setOnClickListener(v -> {
                saveMenuAlpha();
                hideAlphaPanel();
                Toast.makeText(this, "透明度已保存", Toast.LENGTH_SHORT).show();
            });

            // 取消按钮监听
            btnCancel.setOnClickListener(v -> {
                // 恢复原来的透明度
                android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                currentMenuAlpha = prefs.getFloat(KEY_MENU_ALPHA, DEFAULT_MENU_ALPHA);
                updateMenuAlpha();
                hideAlphaPanel();
            });

            alphaPanel = panel;

            // 添加到窗口
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    panelWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(alphaPanel, params);
            isAlphaPanelVisible = true;

        } catch (Exception e) {
            android.util.Log.e("FloatingService", "显示透明度调整面板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 隐藏透明度调整面板
     */
    private void hideAlphaPanel() {
        if (!isAlphaPanelVisible) return;
        try {
            if (alphaPanel != null && alphaPanel.isAttachedToWindow()) {
                windowManager.removeView(alphaPanel);
                alphaPanel = null;
            }
            isAlphaPanelVisible = false;
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "隐藏透明度调整面板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新菜单透明度（实时预览）
     */
    private void updateMenuAlpha() {
        if (menuParams != null && floatingMenu != null && floatingMenu.isAttachedToWindow()) {
            menuParams.alpha = currentMenuAlpha;
            windowManager.updateViewLayout(floatingMenu, menuParams);
        }
    }

    /**
     * 保存菜单透明度
     */
    private void saveMenuAlpha() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putFloat(KEY_MENU_ALPHA, currentMenuAlpha).apply();
    }

    /**
     * 打开QQ群
     */
    private void openQQGroup() {
        String qqGroupScheme = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=927046503&card_type=group";
        try {
            // 隐藏功能菜单
            hideMenu();
            
            // 尝试打开QQ
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(qqGroupScheme));
            intent.setPackage("com.tencent.mobileqq");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d("FloatingService", "已打开QQ群");
        } catch (Exception e) {
            // QQ未安装或打开失败
            Log.e("FloatingService", "打开QQ群失败: " + e.getMessage(), e);
            Toast.makeText(this, "请先安装QQ", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 刷新菜单高度
     */
    private void refreshMenuHeight() {
        if (!isMenuVisible || floatingMenu == null) return;

        try {
            // 获取屏幕尺寸
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            int horizontalMargin = 120;
            int verticalMargin = 200;
            int menuWidth = screenWidth - horizontalMargin * 2;

            // 重新测量菜单内容实际高度
            floatingMenu.measure(
                    View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int contentHeight = floatingMenu.getMeasuredHeight();
            int maxHeight = screenHeight - verticalMargin * 2;
            int menuHeight = Math.min(contentHeight, maxHeight);

            // 更新菜单布局参数
            if (menuParams != null) {
                menuParams.height = menuHeight;
                windowManager.updateViewLayout(floatingMenu, menuParams);
                Log.d("FloatingService", "菜单高度已刷新: " + menuHeight);
            }
        } catch (Exception e) {
            Log.e("FloatingService", "刷新菜单高度失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新标签页内容
     */
    private void updateContentForTab(int tabIndex) {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();
        
        switch (tabIndex) {
            case 0: // 全部功能
                addButtonsToMenu();
                break;
            case 1: // 三元三件
                addSanyuanSanjianButtons();
                break;
            case 2: // 兑换过肥料
                addDuihuanFeiliao();
                break;
            case 3: // E卡猫超
                addEkaSearchButtons();
                break;
            case 4: // 地址生成
                addTextRow(contentContainer, "地址生成功能开发中...");
                break;
            case 5: // 查违禁店
                addTextRow(contentContainer, "查违禁店功能开发中...");
                break;
        }
    }

    /**
     * 添加三元三件相关按钮
     */
    private void addSanyuanSanjianButtons() {
        if (contentContainer == null) return;
        
        // 第一组：三元三件, 4W1, 4W2, 购物车
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("三元三件", "", UrlConstants.URL_SANYUAN_SANJIAN),
                new ButtonData("4W1", "1次", UrlConstants.URL_4W1),
                new ButtonData("4W2", "1次", UrlConstants.URL_4W2),
                new ButtonData("购物车", "", UrlConstants.URL_GOUWUCHE)
        });

        // 第二组：代付款, 地址管理, 肥料明细, 3W1
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("代付款", "", UrlConstants.URL_DAIFUKUAN),
                new ButtonData("地址管理", "", UrlConstants.URL_DIZHI_GUANLI),
                new ButtonData("肥料明细", "", UrlConstants.URL_FEILIAO_MINGXI),
                new ButtonData("3W1", "10次", UrlConstants.URL_3W1)
        });

        // 第三组：3W2, 3W3, 3W4, 店铺主页
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("3W2", "3次", UrlConstants.URL_3W2),
                new ButtonData("3W3", "1次", UrlConstants.URL_3W3),
                new ButtonData("3W4", "1次", UrlConstants.URL_3W4),
                new ButtonData("店铺主页", "点淘", UrlConstants.URL_DIANTAO_SHOP)
        });

        // 第四组：购物车, 代付款
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("购物车", "点淘", UrlConstants.URL_DIANTAO_CART),
                new ButtonData("代付款", "点淘", UrlConstants.URL_DIANTAO_DAIFUKUAN),
                null, // 空位
                null  // 空位
        });

        // 提示文本
        addTextRow(contentContainer, "此页仅展示涉及到三元三件的功能");
    }

    /**
     * 添加兑换过肥料相关按钮
     */
    private void addDuihuanFeiliao() {
        if (contentContainer == null) return;
        
        // 第一组：农场兑换, 4W1, 4W2, 购物车
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("农场兑换", "", UrlConstants.URL_NONGCHANG_DUIHUAN),
                new ButtonData("4W1", "1次", UrlConstants.URL_4W1),
                new ButtonData("4W2", "1次", UrlConstants.URL_4W2),
                new ButtonData("购物车", "", UrlConstants.URL_GOUWUCHE)
        });

        // 第二组：代付款, 肥料明细, 3W1, 3W2
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("代付款", "", UrlConstants.URL_DAIFUKUAN),
                new ButtonData("肥料明细", "", UrlConstants.URL_FEILIAO_MINGXI),
                new ButtonData("3W1", "10次", UrlConstants.URL_3W1),
                new ButtonData("3W2", "3次", UrlConstants.URL_3W2)
        });

        // 第三组：3W3, 3W4
        addButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("3W3", "1次", UrlConstants.URL_3W3),
                new ButtonData("3W4", "1次", UrlConstants.URL_3W4),
                null, // 空位
                null  // 空位
        });

        // 提示文本
        addTextRow(contentContainer, "此页仅展示涉及到兑换过肥料的功能");
    }

    /**
     * 添加E卡搜索界面
     */
    private void addEkaSearchButtons() {
        if (contentContainer == null) return;
        
        // 加载搜索关键词
        loadSearchKeywords();
        
        // 创建搜索控制行
        LinearLayout searchRow = new LinearLayout(themedContext);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchRowParams.bottomMargin = 12;
        searchRow.setLayoutParams(searchRowParams);
        
        // "关键词："标签
        android.widget.TextView labelKeyword = new android.widget.TextView(themedContext);
        labelKeyword.setText("关键词：");
        labelKeyword.setTextSize(14 * currentFontScale);
        labelKeyword.setTextColor(0xFF333333);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.rightMargin = 8;
        labelKeyword.setLayoutParams(labelParams);
        
        // 可编辑下拉框
        searchKeywordInput = new android.widget.AutoCompleteTextView(themedContext);
        searchKeywordInput.setHint("输入或选择关键词");
        searchKeywordInput.setTextSize(12 * currentFontScale); // 缩小字体
        searchKeywordInput.setSingleLine(true); // 单行显示
        searchKeywordInput.setMaxLines(1); // 最大1行
        searchKeywordInput.setThreshold(1); // 输入1个字符就显示建议
        searchKeywordInput.setDropDownHeight(400); // 设置下拉列表高度
        // 添加下拉箭头图标
        searchKeywordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        // 点击整个输入框区域都能弹出下拉列表
        searchKeywordInput.setOnClickListener(v -> searchKeywordInput.showDropDown());
        
        // 焦点监听：输入法弹出时调整菜单位置
        final int[] originalMenuY = {0};
        searchKeywordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点，输入法即将弹出
                android.util.Log.d("FloatingService", "输入框获得焦点");
                // 保存原始Y坐标
                if (menuParams != null) {
                    originalMenuY[0] = menuParams.y;
                    // 将菜单上移，确保输入框可见
                    android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(dm);
                    int screenHeight = dm.heightPixels;
                    // 上移到屏幕上半部分
                    menuParams.y = -screenHeight / 4;
                    menuParams.gravity = Gravity.CENTER;
                    try {
                        windowManager.updateViewLayout(floatingMenu, menuParams);
                    } catch (Exception e) {
                        android.util.Log.e("FloatingService", "调整菜单位置失败", e);
                    }
                }
            } else {
                // 失去焦点，输入法关闭
                android.util.Log.d("FloatingService", "输入框失去焦点");
                // 恢复原始位置
                if (menuParams != null) {
                    menuParams.y = originalMenuY[0];
                    menuParams.gravity = Gravity.CENTER;
                    try {
                        windowManager.updateViewLayout(floatingMenu, menuParams);
                    } catch (Exception e) {
                        android.util.Log.e("FloatingService", "恢复菜单位置失败", e);
                    }
                }
            }
        });
        
        keywordAdapter = new android.widget.ArrayAdapter<>(
                themedContext,
                android.R.layout.simple_dropdown_item_1line,
                searchKeywords
        );
        searchKeywordInput.setAdapter(keywordAdapter);
        // 设置默认显示第一个关键词
        if (!searchKeywords.isEmpty()) {
            searchKeywordInput.setText(searchKeywords.get(0));
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        inputParams.rightMargin = 4;
        searchKeywordInput.setLayoutParams(inputParams);
        
        // 保存按钮
        MaterialButton btnSave = new MaterialButton(themedContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnSave.setText("保存");
        btnSave.setTextSize(11 * currentFontScale); // 缩小字体
        btnSave.setMinWidth(0);
        btnSave.setMinHeight(0);
        btnSave.setInsetTop(0);
        btnSave.setInsetBottom(0);
        btnSave.setPadding(12, 8, 12, 8); // 减小内边距
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveParams.rightMargin = 4;
        btnSave.setLayoutParams(saveParams);
        btnSave.setOnClickListener(v -> saveCurrentKeyword());
        
        // 帮助图标
        android.widget.ImageView ivHelp = new android.widget.ImageView(themedContext);
        ivHelp.setImageResource(android.R.drawable.ic_menu_help);
        ivHelp.setColorFilter(0xFF666666);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
                (int)(24 * getResources().getDisplayMetrics().density), // 缩小尺寸
                (int)(24 * getResources().getDisplayMetrics().density)
        );
        ivHelp.setLayoutParams(helpParams);
        ivHelp.setOnClickListener(v -> showEkaSearchHelpDialog());
        
        searchRow.addView(labelKeyword);
        searchRow.addView(searchKeywordInput);
        searchRow.addView(btnSave);
        searchRow.addView(ivHelp);
        contentContainer.addView(searchRow);
        
        // 添加按钮（使用特殊的点击处理）
        // 第一组：6W, 4W1, 4W2, 购物车
        addEkaButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("6W", "1次", UrlConstants.URL_6W),
                new ButtonData("4W①", "1次", UrlConstants.URL_4W1),
                new ButtonData("4W②", "1次", UrlConstants.URL_4W2),
                new ButtonData("购物车", "", UrlConstants.URL_GOUWUCHE)
        });
        
        // 第二组：代付款, 地址管理, 肥料明细, 3W1
        addEkaButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("代付款", "", UrlConstants.URL_DAIFUKUAN),
                new ButtonData("地址管理", "", UrlConstants.URL_DIZHI_GUANLI),
                new ButtonData("肥料明细", "", UrlConstants.URL_FEILIAO_MINGXI),
                new ButtonData("3W①", "10次", UrlConstants.URL_3W1)
        });
        
        // 第三组：3W2, 3W3, 3W4, 空
        addEkaButtonRow(contentContainer, new ButtonData[]{
                new ButtonData("3W②", "3次", UrlConstants.URL_3W2),
                new ButtonData("3W③", "1次", UrlConstants.URL_3W3),
                new ButtonData("3W④", "1次", UrlConstants.URL_3W4),
                null
        });
    }
    
    /**
     * 添加E卡搜索按钮行（带关键词替换功能）
     */
    private void addEkaButtonRow(LinearLayout container, ButtonData[] buttons) {
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
            if (buttons[i] != null) {
                addEkaButton(row, buttons[i], i < buttons.length - 1);
            } else {
                addEmptySpace(row, i < buttons.length - 1);
            }
        }

        container.addView(row);
    }
    
    /**
     * 添加E卡搜索按钮（点击时替换URL中的q参数）
     */
    private void addEkaButton(LinearLayout row, ButtonData data, boolean hasMargin) {
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

        MaterialButton btn = new MaterialButton(themedContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(data.text);
        btn.setTextSize(13 * currentFontScale);
        btn.setMinHeight(44);
        btn.setCornerRadius(8);
        btn.setStrokeWidth(1);
        btn.setStrokeColorResource(android.R.color.darker_gray);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        btn.setTextColor(0xFF333333);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setPadding(4, 4, 4, 4);

        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(btnParams);

        final String baseUrl = data.url;
        btn.setOnClickListener(v -> {
            String keyword = getCurrentSearchKeyword();
            String finalUrl = replaceUrlQueryParam(baseUrl, "q", keyword);
            openTaobaoUrl(finalUrl);
            hideMenu();
        });
        
        frame.addView(btn);

        if (hasBadge) {
            android.widget.TextView badge = new android.widget.TextView(themedContext);
            badge.setText(data.badge);
            badge.setTextSize(9 * currentFontScale);
            badge.setTextColor(0xFFFF6200);
            android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END | android.view.Gravity.TOP
            );
            badgeParams.topMargin = 6;
            badgeParams.rightMargin = 8;
            badge.setLayoutParams(badgeParams);
            frame.addView(badge);
        }

        row.addView(frame);
    }
    
    /**
     * 加载搜索关键词
     */
    private void loadSearchKeywords() {
        searchKeywords.clear();
        // 添加默认关键词
        searchKeywords.add("京东E卡1元");
        searchKeywords.add("享淘卡1元");
        searchKeywords.add("猫超卡1元");
        
        // 加载用户保存的关键词
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_SEARCH_KEYWORDS, "");
        if (!saved.isEmpty()) {
            String[] keywords = saved.split("\\|");
            for (String keyword : keywords) {
                if (!keyword.isEmpty() && !searchKeywords.contains(keyword)) {
                    searchKeywords.add(keyword);
                }
            }
        }
    }
    
    /**
     * 获取当前选中的搜索关键词
     */
    private String getCurrentSearchKeyword() {
        if (searchKeywordInput != null) {
            String keyword = searchKeywordInput.getText().toString().trim();
            if (!keyword.isEmpty()) {
                return keyword;
            }
        }
        return "京东E卡1元"; // 默认值
    }
    
    /**
     * 保存当前关键词
     */
    private void saveCurrentKeyword() {
        String keyword = getCurrentSearchKeyword();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "关键词不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!searchKeywords.contains(keyword)) {
            searchKeywords.add(keyword);
            keywordAdapter.notifyDataSetChanged();
            
            // 保存到SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < searchKeywords.size(); i++) { // 跳过前3个默认关键词
                if (sb.length() > 0) sb.append("|");
                sb.append(searchKeywords.get(i));
            }
            prefs.edit().putString(KEY_SEARCH_KEYWORDS, sb.toString()).apply();
            
            Toast.makeText(this, "关键词已保存", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "关键词已存在", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示E卡搜索帮助对话框
     */
    private void showEkaSearchHelpDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("使用说明")
                .setMessage("自定义搜索关键词后，点击4W,3W的肥料页可自动搜索为设置的关键词")
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
     * 替换URL中的查询参数
     */
    private String replaceUrlQueryParam(String url, String paramName, String newValue) {
        try {
            String encodedValue = java.net.URLEncoder.encode(newValue, "UTF-8");
            // 使用正则替换q参数
            String pattern = "([?&])" + paramName + "=([^&]*)";
            String replacement = "$1" + paramName + "=" + encodedValue;
            return url.replaceAll(pattern, replacement);
        } catch (Exception e) {
            Log.e("FloatingService", "替换URL参数失败", e);
            return url;
        }
    }

    /**
     * 显示快速访问帮助对话框
     */
    private void showQuickAccessHelpDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("快速访问使用说明")
                .setMessage("长按全部功能中按钮可添加到快速访问，长按快速访问上的功能也可以移出快速访问位置")
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
     * 加载快速访问按钮
     */
    private void loadQuickAccessButtons() {
        quickAccessButtons.clear();
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        for (int i = 0; i < MAX_QUICK_ACCESS; i++) {
            String data = prefs.getString(KEY_QUICK_ACCESS + i, null);
            if (data != null && !data.isEmpty()) {
                String[] parts = data.split("\\|");
                if (parts.length == 3) {
                    quickAccessButtons.add(new ButtonData(parts[0], parts[1], parts[2]));
                } else {
                    quickAccessButtons.add(null);
                }
            } else {
                quickAccessButtons.add(null);
            }
        }
    }

    /**
     * 保存快速访问按钮
     */
    private void saveQuickAccessButtons() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        for (int i = 0; i < quickAccessButtons.size(); i++) {
            ButtonData data = quickAccessButtons.get(i);
            if (data != null) {
                editor.putString(KEY_QUICK_ACCESS + i, data.text + "|" + data.badge + "|" + data.url);
            } else {
                editor.remove(KEY_QUICK_ACCESS + i);
            }
        }
        editor.apply();
    }

    /**
     * 更新快速访问视图
     */
    private void updateQuickAccessView() {
        if (quickAccessContainer == null) return;
        quickAccessContainer.removeAllViews();
        
        for (int i = 0; i < MAX_QUICK_ACCESS; i++) {
            final int index = i;
            ButtonData data = i < quickAccessButtons.size() ? quickAccessButtons.get(i) : null;
            
            android.widget.FrameLayout frame = new android.widget.FrameLayout(themedContext);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            if (i < MAX_QUICK_ACCESS - 1) {
                frameParams.rightMargin = 4;
            }
            frame.setLayoutParams(frameParams);
            
            if (data == null) {
                // 空位置，显示添加图标
                MaterialButton btn = new MaterialButton(themedContext, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText("+");
                btn.setTextSize(16 * currentFontScale);
                btn.setMinHeight(44);
                btn.setCornerRadius(8);
                btn.setStrokeWidth(1);
                btn.setStrokeColorResource(android.R.color.darker_gray);
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF5F5F5));
                btn.setTextColor(0xFF999999);
                btn.setOnClickListener(v -> showQuickAccessHelpDialog());
                frame.addView(btn);
            } else {
                // 已添加的按钮
                MaterialButton btn = new MaterialButton(themedContext, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(data.text);
                btn.setTextSize(13 * currentFontScale);
                btn.setMinHeight(44);
                btn.setCornerRadius(8);
                btn.setStrokeWidth(1);
                btn.setStrokeColorResource(android.R.color.darker_gray);
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                btn.setTextColor(0xFF333333);
                
                final String url = data.url;
                btn.setOnClickListener(v -> {
                    openTaobaoUrl(url);
                    hideMenu();
                });
                
                // 长按移除
                btn.setOnLongClickListener(v -> {
                    showRemoveQuickAccessDialog(index);
                    return true;
                });
                
                frame.addView(btn);
                
                // 添加badge
                if (data.badge != null && !data.badge.isEmpty()) {
                    android.widget.TextView badge = new android.widget.TextView(themedContext);
                    badge.setText(data.badge);
                    badge.setTextSize(9 * currentFontScale);
                    badge.setTextColor(0xFFFF6200);
                    android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.END | android.view.Gravity.TOP
                    );
                    badgeParams.topMargin = 6;
                    badgeParams.rightMargin = 8;
                    badge.setLayoutParams(badgeParams);
                    frame.addView(badge);
                }
            }
            
            quickAccessContainer.addView(frame);
        }
    }

    /**
     * 显示添加到快速访问的对话框
     */
    private void showQuickAccessDialog(ButtonData data) {
        // 查找空位置
        int emptyIndex = -1;
        for (int i = 0; i < MAX_QUICK_ACCESS; i++) {
            if (i >= quickAccessButtons.size() || quickAccessButtons.get(i) == null) {
                emptyIndex = i;
                break;
            }
        }
        
        if (emptyIndex == -1) {
            Toast.makeText(this, "快速访问位置已满，长按已有按钮可移除", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final int index = emptyIndex;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("添加到快速访问")
                .setMessage("是否将\"" + data.text + "\"添加到快速访问？")
                .setPositiveButton("添加", (dialog, which) -> {
                    while (quickAccessButtons.size() <= index) {
                        quickAccessButtons.add(null);
                    }
                    quickAccessButtons.set(index, data);
                    saveQuickAccessButtons();
                    updateQuickAccessView();
                    Toast.makeText(this, "已添加到快速访问", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null);
        
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    /**
     * 显示移除快速访问的对话框
     */
    private void showRemoveQuickAccessDialog(int index) {
        ButtonData data = quickAccessButtons.get(index);
        if (data == null) return;
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(themedContext);
        builder.setTitle("移除快速访问")
                .setMessage("是否将\"" + data.text + "\"从快速访问移除？")
                .setPositiveButton("移除", (dialog, which) -> {
                    quickAccessButtons.set(index, null);
                    saveQuickAccessButtons();
                    updateQuickAccessView();
                    Toast.makeText(this, "已移除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null);
        
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
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
                    CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_DEFAULT  // 提高重要性，减少被系统限制
            );
            channel.setDescription("保持悬浮窗服务运行");
            channel.setShowBadge(false);  // 不显示角标
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // 提高优先级，减少被系统回收
                .setOngoing(true)  // 设置为持续通知，不可滑动删除
                .build();
    }
}
