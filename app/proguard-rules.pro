# Keep Room entities & DTOs (Gson maps JSON by field name, so these must not be renamed)
-keep class com.papertrader.app.data.local.entity.** { *; }
-keep class com.papertrader.app.data.remote.** { *; }
-keep class com.papertrader.app.domain.model.** { *; }

# Retrofit / OkHttp / Gson
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
