# Jsoup has optional re2j support we don't ship.
-dontwarn com.google.re2j.**
# OkHttp platform classes.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# kotlinx.serialization (export file models in :domain).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.nexxlabs.caddis.domain.export.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
