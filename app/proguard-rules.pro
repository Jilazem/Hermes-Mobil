# kotlinx.serialization — @Serializable sınıfların üretilmiş serializer'ları korunur
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hermes.mobile.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.hermes.mobile.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
