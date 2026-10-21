# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# J2ME API's and extensions
-keep public class com.j_phone.** { public protected *; }
-keep public class com.jblend.** { public protected *; }
-keep public class com.kddi.** { public protected *; }
-keep public class com.mascotcapsule.micro3d.v3.* { public protected *; }
-keep public class com.mexa.** { public protected *; }
-keep public class com.motorola.** { public protected *; }
-keep public class com.nokia.mid.** { public protected *; }
-keep public class com.samsung.util.** { public protected *; }
-keep public class com.siemens.mp.** { public protected *; }
-keep public class com.sonyericsson.accelerometer.** { public protected *; }
-keep public class com.sprintpcs.media.** { public protected *; }
-keep public class com.sun.midp.midlet.** { public protected *; }
-keep public class com.vodafone.** { public protected *; }
-keep public class javax.** { public protected *; }
-keep public class mmpp.media.** { public protected *; }

# emulator
-keep public class org.microemu.** { public protected *; }

# SnapVM 即时存档：捕获/恢复宿主对象靠私有字段名反射（Graphics.clip/image/
# translate*, RecordStoreImpl.recordStoreName, Player impls 的 listeners/level 等，
# ReflectHandle 还存 "类名|字段名" 串再 Class.forName）。release 混淆改名后
# 反射直接 NoSuchFieldException（debug 不混淆所以只在 release 炸）。
# 全成员 keep 也顺带冻结名字，保证快照文件跨版本兼容。
-keep class javax.microedition.** { *; }
-keep class ru.woesss.j2me.** { *; }
-keep class org.snapvm.** { *; }
-keep class mmpp.media.** { *; }
# Keep the BuildConfig
-keep class ru.playsoftware.j2meloader.BuildConfig { *; }
-keep class ru.playsoftware.j2meloader.util.SparseIntArrayAdapter { *; }
-keep class ru.playsoftware.j2meloader.crashes.models.* { *; }

# dependencies
-keep class com.arthenica.mobileffmpeg.** { *; }
-keep public class org.acra.** { public protected *; }
-keepclassmembers,allowobfuscation class * {
 @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-obfuscationdictionary dictionary.pro
-classobfuscationdictionary dictionary.pro
-repackageclasses
