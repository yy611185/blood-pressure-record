
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

# --- Apache POI (XLSX export/import) ---
# POI 自身由静态调用链保留；OOXML schema 仍通过 XMLBeans 元数据按名称解析。
# 仅保留 schema，避免把整个 POI API 强制塞进 Release。
-keep class org.openxmlformats.** { *; }
# XMLBeans 根据 TypeSystemHolder 的原始类名定位同包下的 .xsb schema 资源。
# R8 若重命名这些入口类，XSSFWorkbook 会在读取 styles.xml 时初始化失败。
-keep class org.apache.poi.schemas.ooxml.system.**.TypeSystemHolder { *; }
-keep class org.apache.xmlbeans.metadata.system.**.TypeSystemHolder { *; }
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

# Compose 与 DataStore 依赖自带 consumer ProGuard 规则，无需整包 keep。
# 整包保留 Compose 或 material-icons-extended 会显著增大包体。

# --- General: Keep Enums ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
