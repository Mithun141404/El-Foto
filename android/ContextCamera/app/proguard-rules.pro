# Context Camera ProGuard rules
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keep class com.contextcamera.app.network.** { *; }
-keepclassmembers class com.contextcamera.app.network.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
