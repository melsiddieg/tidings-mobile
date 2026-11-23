# Keep the Tidings Android WebView JS bridge intact in release builds.
-keep class org.tidings.reader.TidingsBridge { *; }
-keepclassmembers class org.tidings.reader.TidingsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
