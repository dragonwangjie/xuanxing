# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# keep kotlinx serializable classes
-keep @kotlinx.serialization.Serializable class * {*;}

# keep jlatexmath
-keep class org.scilab.forge.jlatexmath.** {*;}

-dontwarn com.google.re2j.**

# Ktor 在 Android 上引用了仅 JVM 可用的 java.lang.management 类（IntellijIdeaDebugDetector）
# Android 不包含这些类，需要告知 R8 忽略
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# java.beans is not available on Android; Jackson references it only on JVM
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# auth0/jackson: TypeReference subclasses rely on runtime generic signatures.
# R8 strips Signature/InnerClasses/EnclosingMethod by default, and its class
# merging/inlining optimizations can destroy the anonymous class hierarchy that
# TypeReference.<init> depends on via getClass().getGenericSuperclass().
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.fasterxml.jackson.** { *; }
-keep class com.auth0.jwt.** { *; }

# ======================================================================
# 玄星：开启混淆后的 keep 规则（保证反射/序列化框架不被混淆破坏）
# ======================================================================

# ---- 注解 / 泛型信息（序列化、反射都依赖） ----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisible*Annotations, AnnotationDefault

# ---- kotlinx.serialization ----
# 保留所有 @Serializable 类自动生成的 $$serializer 伴生对象与 serializer() 方法
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class me.rerere.**$$serializer { *; }
-keepclassmembers class me.rerere.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,allowshrinking class me.rerere.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留所有本项目 @Serializable 数据类的成员（配置/会话/模型都靠它存 JSON）
-keep @kotlinx.serialization.Serializable class me.rerere.** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# ---- Koin（依赖注入，用反射构造 ViewModel / 单例） ----
-keep class org.koin.** { *; }
-keepnames class * { @org.koin.core.annotation.* <methods>; }
-dontwarn org.koin.**

# ---- Room（编译期生成 _Impl 类，运行时反射加载） ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }

# ---- Retrofit / OkHttp / Okio ----
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- Ktor client ----
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# ---- Kotlin 元数据 / 协程 ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- 枚举（序列化常用） ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Parcelable ----
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ---- 反射构造的 ViewModel ----
-keep class * extends androidx.lifecycle.ViewModel { *; }
