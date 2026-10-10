# ── 玄星 Release 混淆规则 ─────────────────────────────────────────
# 背景：release 构建开启了 R8 代码混淆（isMinifyEnabled=true）。
# MCP 工具调用、设置持久化、OpenAI/Anthropic 请求体等核心链路全部依赖
# kotlinx.serialization 生成的 serializer 与运行时 Companion/metadata 反射查找；
# R8 全模式默认会破坏这些构件，典型症状即「MCP 工具传递参数失败 /
# Serializer for class ... is not found / arguments 丢失」。
# 本文件 = kotlinx.serialization 官方推荐规则 + MCP SDK + JNI 兜底。

# ── 属性保留（注解/泛型签名/Kotlin metadata 运行时可见）──────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }
-dontnote kotlinx.serialization.**

# ── kotlinx.serialization 官方推荐规则 ────────────────────────────
# Keep `Companion` object field of serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}
# Keep names for named companion object from obfuscation
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}
# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
-dontnote kotlinx.serialization.**
# Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
-dontwarn kotlinx.serialization.internal.ClassValueReferences
# disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}
# Rule to save runtime annotations on serializable class.
# If the R8 full mode is used, annotations are removed from classes-files.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ── 玄星自有包：settings/datastore/ai 的 DTO 走名称/反射查找 ──────
-keep,includedescriptorclasses class me.rerere.rikkahub.**$$serializer { *; }
-keepclassmembers class me.rerere.rikkahub.** {
    *** Companion;
}
-keepclasseswithmembers class me.rerere.rikkahub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class me.rerere.ai.**$$serializer { *; }
-keepclassmembers class me.rerere.ai.** {
    *** Companion;
}
-keepclasseswithmembers class me.rerere.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── MCP 协议 SDK：全部消息/类型（JSON-RPC、tools/call arguments）──
# 协议 DTO 的类名与成员名都会参与运行时查找，整体不参与混淆，体积代价极小。
-keep class io.modelcontextprotocol.** { *; }

# ── 枚举：R8 full mode 会移除未被直接引用的 values()/valueOf()，
#    而序列化器与配置回读经常反射调用它们 ──────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── JNI：workspace/termux 的 native 方法按名字绑定，禁止改名 ──────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── 三方库噪音抑制（缺类是 Android 平台正常现象，不阻断构建）──────
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn java.awt.**