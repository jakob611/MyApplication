# Add project specific ProGuard rules here.

# Keep line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin ----
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# Firestore model classes (data classes serialized by Firestore)
-keep class com.example.myapplication.data.** { *; }

# ---- Gson / JSON ----
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- osmdroid (karte za RunTracker) ----
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ---- Health Connect ----
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# ---- WorkManager ----
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- CameraX ----
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---- ML Kit (barcode scanning) ----
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- ExoPlayer / Media3 ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- TFLite / LiteRT (meal_ranker.tflite) ----
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.**
-dontwarn com.google.ai.edge.**

# ---- Widget providers ----
-keep class com.example.myapplication.widget.** { *; }
-keep class com.example.myapplication.worker.** { *; }
-keep class com.example.myapplication.workers.** { *; }

# ---- AppCompat / Material ----
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# ---- Navigation ----
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ---- Enum classes (pogosto se zlomijo z R8) ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Parcelable ----
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ---- Serializable ----
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
