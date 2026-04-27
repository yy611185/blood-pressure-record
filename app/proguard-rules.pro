
# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- kotlinx.datetime ---
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

# --- Apache POI (XLSX export/import) ---
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3c.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.xmlbeans.**
-dontwarn com.microsoft.**
-dontwarn org.junit.**
-dontwarn javax.xml.stream.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**

# --- DataStore Preferences ---
-keep class androidx.datastore.** { *; }

# --- Compose (safe defaults) ---
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# --- General: Keep Enums ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
