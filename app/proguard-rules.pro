# Cricket App ProGuard Rules - Disable Optimization
# Use this configuration when optimization causes critical functionality to break

# Disable optimization completely
-dontoptimize
-dontpreverify

# Only obfuscate but don't remove any code
-dontusemixedcaseclassnames
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Keep ALL classes and methods in your app
-keep class com.cricketApp.cric.** { *; }

# Keep all classes from Google Play Services and Firebase
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

# Keep ALL Android support library classes
-keep class androidx.** { *; }
-keep class android.** { *; }

# Keep all external libraries
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Handle all warnings as just warnings, not errors
-dontwarn **

# Keep required metadata
-keepattributes *Annotation*,Signature,Exceptions
-keepattributes SourceFile,LineNumberTable

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep resource references
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep app components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider