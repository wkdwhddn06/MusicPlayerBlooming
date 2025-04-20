# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/hemanths/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
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

# Preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn java.lang.invoke.*
-dontwarn **$$Lambda$*
-dontwarn javax.annotation.**
-dontwarn org.commonmark.ext.gfm.strikethrough.**

# RetroFit
-dontwarn retrofit.**
-keep class retrofit.** { *; }
# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep interface com.squareup.okhttp3.** { *; }
-dontwarn com.squareup.okhttp3.**

# Ktor (workaround for AGP 8.8.0)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

#-dontwarn
#-ignorewarnings

#Jaudiotagger
-dontwarn org.jaudiotagger.**
-dontwarn org.jcodec.**
-keep class org.jaudiotagger.** { *; }
-keep class org.jcodec.** { *; }

-keepclassmembers enum * { *; }
-keepattributes *Annotation*, Signature, Exception
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.fragment.app.Fragment{}
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable
-keep class com.uniqtech.musicplayer.database.LyricsEntity
-keep class com.uniqtech.musicplayer.http.deezer.model.** { *; }
-keep class com.uniqtech.musicplayer.http.lastfm.model.** { *; }
-keep class com.uniqtech.musicplayer.model.** { *; }
-keep class com.uniqtech.musicplayer.search.SearchFilter { *; }
-keep class com.uniqtech.musicplayer.update.** { *; }
-keep class com.google.android.material.bottomsheet.** { *; }
-keep class com.google.android.material.transition.** { *; }

# Keep player
-keep class com.uniqtech.musicplayer.fragments.player.base.** { *; }
-keep class com.uniqtech.musicplayer.fragments.player.styles.** { *; }

# Gson - required after agp 8 made r8 full mode default
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking public class * implements java.lang.reflect.Type

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Hide an annoying compilation warning
# http://stackoverflow.com/questions/3308010/what-is-the-ignoring-innerclasses-attribute-warning-output-during-compilation
-keepattributes EnclosingMethod