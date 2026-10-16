# kotlinx.serialization: keep generated serializers of backup DTOs
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.kostaking.raspored.data.backup.**$$serializer { *; }
-keepclassmembers class com.kostaking.raspored.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.kostaking.raspored.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
