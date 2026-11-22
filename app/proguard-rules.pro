# Add project specific ProGuard rules here.
# Keep Moshi DTOs and domain models used by reflection
-keep class com.example.data.network.** { *; }
-keep class com.example.model.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
