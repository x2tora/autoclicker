-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all kotlinx.serialization models, NavKey routes, serializers, and companions
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xitora.autoclicker.model.** { *; }
-keep,includedescriptorclasses class com.xitora.autoclicker.ui.screens.AppRoute { *; }
-keep,includedescriptorclasses class com.xitora.autoclicker.ui.screens.AppRoute$** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep ViewModel constructors and accessibility service components
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.xitora.autoclicker.viewmodel.** { *; }
-keep class com.xitora.autoclicker.service.** { *; }

-dontwarn top.yukonga.miuix.**
-dontwarn kotlinx.serialization.**
