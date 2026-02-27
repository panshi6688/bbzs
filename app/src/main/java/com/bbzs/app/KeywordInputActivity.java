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
        
        // 创建布局
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFFF5F5F5);
        rootLayout.setPadding(60, 100, 60, 100);
        rootLayout.setGravity(Gravity.CENTER);
        
        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText("输入搜索关键词");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(0xFF333333);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 40);
        rootLayout.addView(tvTitle);
        
        // 输入框
        etKeyword = new EditText(this);
        String currentKeyword = getIntent().getStringExtra(EXTRA_CURRENT_KEYWORD);
        if (currentKeyword != null && !currentKeyword.equals("点击输入关键词")) {
            etKeyword.setText(currentKeyword);
            etKeyword.setSelection(currentKeyword.length());
        }
        etKeyword.setSingleLine(true);
        etKeyword.setTextSize(16);
        etKeyword.setPadding(30, 20, 30, 20);
        etKeyword.setBackgroundColor(0xFFFFFFFF);
        etKeyword.setHint("请输入关键词");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, 30);
        rootLayout.addView(etKeyword, inputParams);
        
        // 历史记录标题
        TextView tvHistory = new TextView(this);
        tvHistory.setText("历史记录（点击选择）：");
        tvHistory.setTextSize(14);
        tvHistory.setTextColor(0xFF666666);
        tvHistory.setPadding(0, 20, 0, 10);
        rootLayout.addView(tvHistory);
        
        // 加载历史记录
        loadSearchKeywords();
        
        // 历史记录列表
        lvHistory = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                searchKeywords
        );
        lvHistory.setAdapter(adapter);
        lvHistory.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        listParams.setMargins(0, 0, 0, 30);
        rootLayout.addView(lvHistory, listParams);
        
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
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        btnParams.setMargins(0, 0, 20, 0);
        buttonLayout.addView(btnCancel, btnParams);
        
        // 保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存");
        btnSave.setTextSize(16);
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
                
                // 发送广播通知Service
                Intent broadcast = new Intent("com.bbzs.app.KEYWORD_RESULT");
                broadcast.putExtra(EXTRA_RESULT_KEYWORD, keyword);
                sendBroadcast(broadcast);
                
                finish();
            } else {
                Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 取消按钮点击
        btnCancel.setOnClickListener(v -> {
            // 发送取消广播
            Intent broadcast = new Intent("com.bbzs.app.KEYWORD_CANCEL");
            sendBroadcast(broadcast);
            finish();
        });
    }
    
    @Override
    public void onBackPressed() {
        // 返回键也发送取消广播
        Intent broadcast = new Intent("com.bbzs.app.KEYWORD_CANCEL");
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
