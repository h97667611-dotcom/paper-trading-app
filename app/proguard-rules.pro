# Keep Room entities & DTOs (reflection-free but keep names for clarity in stack traces)
-keep class com.papertrader.app.data.local.entity.** { *; }
-keep class com.papertrader.app.data.remote.**.dto.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
