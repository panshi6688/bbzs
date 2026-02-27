package com.bbzs.app;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 调试日志工具类，将日志保存到本地文件
 */
public class DebugLogger {
    private static File logFile;
    private static boolean isEnabled = true;
    
    /**
     * 初始化日志文件
     */
    public static void init(Context context) {
        try {
            // 保存到应用外部存储目录
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                logFile = new File(dir, "floating_service_debug.txt");
                // 清空旧日志
                if (logFile.exists()) {
                    logFile.delete();
                }
                logFile.createNewFile();
                log("===== 日志开始 =====");
                log("时间: " + getCurrentTime());
            }
        } catch (Exception e) {
            android.util.Log.e("DebugLogger", "初始化日志文件失败", e);
        }
    }
    
    /**
     * 写入日志
     */
    public static void log(String message) {
        if (!isEnabled || logFile == null) return;
        
        try {
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(getCurrentTime() + " | " + message + "\n");
            writer.flush();
            writer.close();
            
            // 同时输出到Logcat
            android.util.Log.d("FloatingService", message);
        } catch (IOException e) {
            android.util.Log.e("DebugLogger", "写入日志失败", e);
        }
    }
    
    /**
     * 获取当前时间字符串
     */
    private static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取日志文件路径
     */
    public static String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "未初始化";
    }
}
