# Add project specific ProGuard rules here.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
}

# Useful stack traces in release crashes (method names still obfuscated).
-keepattributes SourceFile,LineNumberTable
