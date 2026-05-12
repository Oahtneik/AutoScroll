# ===========================================================================
# R8 / ProGuard rules for AutoScroll release builds.
# ===========================================================================

# ---- Log stripping ---------------------------------------------------------
# R8's `-assumenosideeffects` lets it remove calls to these methods entirely
# from the release bytecode. We keep warn/error/wtf — those should always be
# captured in crash reports.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ---- Our components --------------------------------------------------------
# These are referenced by name in AndroidManifest.xml; if R8 obfuscates the
# class name the system can't bind to them.
-keep class com.autoscroll.app.AutoScrollApplication             { *; }
-keep class com.autoscroll.app.MainActivity                       { *; }
-keep class com.autoscroll.app.service.AutoScrollAccessibilityService { *; }
-keep class com.autoscroll.app.service.AutoScrollForegroundService    { *; }

# AccessibilityService config XML references the service class by name; keep
# the accessibility-related class metadata so AAPT-generated lookups still work.
-keepclassmembers class com.autoscroll.app.service.AutoScrollAccessibilityService {
    public <init>(...);
    public *** onAccessibilityEvent(***);
    public *** onServiceConnected();
    public *** onInterrupt();
}

# ---- DataStore enums -------------------------------------------------------
# Preferences DataStore serialises enum names as plain strings via reflection
# on the enum class — keep enum values and `valueOf` for safe restore.
-keepclassmembers enum com.autoscroll.app.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Kotlin / Coroutines / Compose ----------------------------------------
# Modern AGP + R8 mostly handles these via default rules in proguard-android-
# optimize.txt and the consumer rules shipped inside each artifact. The
# explicit keeps below are belt-and-braces against future regressions.

# Compose runtime metadata.
-keep class androidx.compose.runtime.** { *; }

# Coroutines internal: keeps stack-trace recovery working in crash reports.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Service / Activity reflection. AndroidX startup + lifecycle do some
# constructor lookup — keep default + Context-only ctors on services.
-keepclassmembers class * extends android.app.Service {
    public <init>();
}
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public <init>();
}

# ---- Suppress benign warnings ---------------------------------------------
# Lifecycle ships annotations referencing javax.annotation.* — harmless at runtime.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
