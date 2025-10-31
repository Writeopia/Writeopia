-keep class org.sqlite.** { *; }
-keep class org.slf4j.**{ *; }
-keep class com.sun.jna.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class * implements com.sun.jna.* { *; }
-keep class io.ktor.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.datetime.** { *; }
-keep class okio.** { *; }
-keep class coil3.PlatformContext { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}
-keep class coil3.network.ktor3.** { *; }

-dontwarn org.slf4j.**
-dontwarn android.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.util.List

