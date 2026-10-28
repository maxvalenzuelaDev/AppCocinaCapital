# Reglas de R8 para el APK release (T-19).

# kotlinx.serialization: los modelos de Supabase (@Serializable) se leen por su serializador generado.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.cocinacapital.app.** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.cocinacapital.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cocinacapital.app.**$$serializer { *; }
# Los enums que llegan de Supabase como texto ("vigente", "canjeado"...) se resuelven por @SerialName.
-keepclassmembers enum com.cocinacapital.app.** { *; }

# Ktor y supabase-kt referencian clases opcionales de la JVM que no existen en Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn io.ktor.util.debug.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Para que las fallas reportadas desde el release indiquen archivo y línea.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
