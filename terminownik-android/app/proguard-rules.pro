# Reguly R8 dla wydania release (minify + shrinkResources = true).

# Zachowaj informacje potrzebne do czytelnych sladow stosu.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# --- kotlinx.serialization (format kopii zapasowej: JSON) ---
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    static <1>$<2>$<3> INSTANCE;
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class pl.terminownik.app.** {
    *** Companion;
}
-keepclasseswithmembers class pl.terminownik.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.terminownik.app.**$$serializer { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- ML Kit (modele wbudowane: barcode-scanning, text-recognition) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- CameraX ---
-dontwarn androidx.camera.**

# --- Enumy uzywane w warstwie danych (konwertery Room) ---
-keepclassmembers enum pl.terminownik.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
