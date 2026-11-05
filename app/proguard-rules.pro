-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# LibretroDroid: la parte nativa llama a métodos y lee campos de estas clases por su nombre (JNI); R8 no puede verlo.
-keep class com.swordfish.libretrodroid.** { *; }
-keepclassmembers class com.swordfish.libretrodroid.** { *; }
