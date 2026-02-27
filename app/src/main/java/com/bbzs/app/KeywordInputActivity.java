package com.bbzs.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 关键词输入Activity - 全屏界面,避免输入法弹出时闪动
 */
public class KeywordInputActivity extends Activity {
    
    public static final String EXTRA_CURRENT_KEYWORD = "current_keyword";
    public static final String EXTRA_RESULT_KEYWORD = "result_keyword";
    public static final int REQUEST_CODE = 1001;
    
    private EditText etKeyword;
    private ListView lvHistory;
    private ArrayList<String> searchKeywords = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建主布局
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFFF8F9FA);
        rootLayout.setPadding(40, 80, 40, 80);
        
        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText("输入搜索关键词");
        tvTitle.setTextSize(22);
        tvTitle.setTextColor(0xFF1A1A1A);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 30);
        rootLayout.addView(tvTitle);
        
        // 输入框容器(带圆角边框)
        LinearLayout inputContainer = new LinearLayout(this);
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        inputContainer.setBackgroundColor(0xFFFFFFFF);
        inputContainer.setPadding(20, 15, 20, 15);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(0xFFFFFFFF);
        inputBg.setCornerRadius(12);
        inputBg.setStroke(2, 0xFFE0E0E0);
        inputContainer.setBackground(inputBg);
        
        LinearLayout.LayoutParams inputContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputContainerParams.setMargins(0, 0, 0, 25);
        rootLayout.addView(inputContainer, inputContainerParams);
        
        // 输入框
        etKeyword = new EditText(this);
        String currentKeyword = getIntent().getStringExtra(EXTRA_CURRENT_KEYWORD);
        if (currentKeyword != null && !currentKeyword.equals("点击输入关键词")) {
            etKeyword.setText(currentKeyword);
            etKeyword.setSelection(currentKeyword.length());
        }
        etKeyword.setSingleLine(true);
        etKeyword.setTextSize(16);
        etKeyword.setTextColor(0xFF333333);
        etKeyword.setHintTextColor(0xFF999999);
        etKeyword.setPadding(10, 15, 10, 15);
        etKeyword.setBackgroundColor(0x00000000); // 透明背景
        etKeyword.setHint("请输入关键词");
        inputContainer.addView(etKeyword, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // 历史记录标题
        TextView tvHistory = new TextView(this);
        tvHistory.setText("历史记录");
        tvHistory.setTextSize(15);
        tvHistory.setTextColor(0xFF666666);
        tvHistory.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHistory.setPadding(10, 15, 0, 12);
        rootLayout.addView(tvHistory);
        
        // 加载历史记录
        loadSearchKeywords();
        
        // 历史记录列表容器
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable listBg = new android.graphics.drawable.GradientDrawable();
        listBg.setColor(0xFFFFFFFF);
        listBg.setCornerRadius(12);
        listBg.setStroke(1, 0xFFE8E8E8);
        listContainer.setBackground(listBg);
        
        // 历史记录列表
        lvHistory = new ListView(this);
        lvHistory.setDivider(new android.graphics.drawable.ColorDrawable(0xFFF0F0F0));
        lvHistory.setDividerHeight(1);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                searchKeywords
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setTextColor(0xFF333333);
                    textView.setTextSize(15);
                    textView.setPadding(20, 18, 20, 18);
                }
                return view;
            }
        };
        lvHistory.setAdapter(adapter);
        lvHistory.setBackgroundColor(0x00000000); // 透明背景
        
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        listParams.setMargins(0, 0, 0, 25);
        listContainer.addView(lvHistory);
        rootLayout.addView(listContainer, listParams);
        
        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            etKeyword.setText(searchKeywords.get(position));
            etKeyword.setSelection(etKeyword.getText().length());
        });
        
        // 按钮容器
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        
        // 取消按钮
        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setTextSize(16);
        btnCancel.setTextColor(0xFF666666);
        android.graphics.drawable.GradientDrawable cancelBg = new android.graphics.drawable.GradientDrawable();
        cancelBg.setColor(0xFFFFFFFF);
        cancelBg.setCornerRadius(8);
        cancelBg.setStroke(2, 0xFFDDDDDD);
        btnCancel.setBackground(cancelBg);
        btnCancel.setPadding(0, 20, 0, 20);
        LinearLayout.LayoutParams btnCancelParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        btnCancelParams.setMargins(0, 0, 15, 0);
        buttonLayout.addView(btnCancel, btnCancelParams);
        
        // 保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存");
        btnSave.setTextSize(16);
        btnSave.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(0xFF1890FF);
        saveBg.setCornerRadius(8);
        btnSave.setBackground(saveBg);
        btnSave.setPadding(0, 20, 0, 20);
        LinearLayout.LayoutParams btnSaveParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        buttonLayout.addView(btnSave, btnSaveParams);
        
        rootLayout.addView(buttonLayout);
        
        setContentView(rootLayout);
        
        // 自动弹出输入法
        etKeyword.requestFocus();
        etKeyword.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etKeyword, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
        
        // 保存按钮点击
        btnSave.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (!keyword.isEmpty()) {
                // 保存到历史
                if (!searchKeywords.contains(keyword)) {
                    searchKeywords.add(keyword);
                    saveSearchKeywords();
                }
                
                android.util.Log.d("KeywordInputActivity", "发送保存广播,关键词: " + keyword);
                
                // 发送广播通知Service
                Intent broadcast = new Intent("com.bbzs.app.KEYWORD_RESULT");
                broadcast.putExtra(EXTRA_RESULT_KEYWORD, keyword);
                broadcast.setPackage(getPackageName()); // 指定包名,确保广播能送达
                sendBroadcast(broadcast);
                
                finish();
            } else {
                Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 取消按钮点击
        btnCancel.setOnClickListener(v -> {
            android.util.Log.d("KeywordInputActivity", "发送取消广播");
            
            // 发送取消广播
            Intent broadcast = new Intent("com.bbzs.app.KEYWORD_CANCEL");
            broadcast.setPackage(getPackageName()); // 指定包名,确保广播能送达
            sendBroadcast(broadcast);
            
            finish();
        });
    }
    
    @Override
    public void onBackPressed() {
        android.util.Log.d("KeywordInputActivity", "返回键,发送取消广播");
        
        // 返回键也发送取消广播
        Intent broadcast = new Intent("com.bbzs.app.KEYWORD_CANCEL");
        broadcast.setPackage(getPackageName()); // 指定包名,确保广播能送达
        sendBroadcast(broadcast);
        
        super.onBackPressed();
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
        android.content.SharedPreferences prefs = getSharedPreferences("bbzs_prefs", MODE_PRIVATE);
        String saved = prefs.getString("search_keywords", "");
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
     * 保存搜索关键词
     */
    private void saveSearchKeywords() {
        android.content.SharedPreferences prefs = getSharedPreferences("bbzs_prefs", MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < searchKeywords.size(); i++) { // 跳过前3个默认关键词
            if (sb.length() > 0) sb.append("|");
            sb.append(searchKeywords.get(i));
        }
        prefs.edit().putString("search_keywords", sb.toString()).apply();
    }
}
