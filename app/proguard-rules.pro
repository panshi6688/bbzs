# Add project specific ProGuard rules here.

# ==================== 基础配置 ====================
# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 保留行号信息，方便调试崩溃日志
-keepattributes SourceFile,LineNumberTable

# 保留泛型信息
-keepattributes Signature

# ==================== Android基础组件 ====================
# 保留Activity、Service、BroadcastReceiver、ContentProvider
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application
-keep public class * extends android.view.View

# 保留自定义View的构造方法
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留View的set/get方法（属性动画需要）
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

# 保留onClick方法
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留Parcelable序列化类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留Serializable序列化类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== 项目特定类 ====================
# 保留应用主要类（根据需要调整）
-keep class com.bbzs.app.MainActivity { *; }
-keep class com.bbzs.app.FloatingService { *; }
-keep class com.bbzs.app.UrlConstants { *; }

# ==================== AndroidX ====================
# AndroidX基础库
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ==================== Material Design ====================
# Material组件
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ==================== WebView ====================
# 保留WebView相关
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# 保留JavaScript接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==================== 反射相关 ====================
# 如果使用了反射，保留相关类
-keep class * {
    public <init>(...);
}

# ==================== 移除日志 ====================
# Release版本移除Log输出
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ==================== 优化配置 ====================
# 优化次数
-optimizationpasses 5

# 不使用大小写混合类名
-dontusemixedcaseclassnames

# 不跳过非公共的库的类
-dontskipnonpubliclibraryclasses

# 混淆时记录日志
-verbose

# 优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification

# 预校验
-dontpreverify

# 混淆算法
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*


