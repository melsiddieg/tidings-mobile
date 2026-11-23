package org.tidings.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Keep
class TidingsBridge(
  private val activity: MainActivity,
  private val webView: WebView
) {
  private fun isTrustedOrigin(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase() ?: return false
    val host = parsed.host?.lowercase() ?: return false
    if ((scheme == "http" || scheme == "https") && host == "tauri.localhost") {
      return true
    }
    if (BuildConfig.DEBUG && (scheme == "http" || scheme == "https") && (host == "localhost" || host == "127.0.0.1")) {
      return true
    }
    return false
  }

  @JavascriptInterface
  fun openExternal(url: String) {
    val parsed = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
    val scheme = parsed.scheme?.lowercase() ?: return
    if (scheme != "http" && scheme != "https" && scheme != "mailto") return
    activity.runOnUiThread {
      if (!isTrustedOrigin(webView.url)) return@runOnUiThread
      runCatching {
        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
      }
    }
  }

  @JavascriptInterface
  fun setAudioPlaybackState(playing: Boolean, title: String, artist: String) {
    val safeTitle = title.trim().take(200)
    val safeArtist = artist.trim().take(200)
    activity.runOnUiThread {
      if (!isTrustedOrigin(webView.url)) return@runOnUiThread
      activity.setAudioPlaying(playing, safeTitle, safeArtist)
    }
  }
}

class MainActivity : TauriActivity() {
  private var currentWebView: WebView? = null
  @Volatile
  private var isAudioPlaying: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
  }

  override fun onPause() {
    super.onPause()
    // WryActivity.onPause() calls mWebView.onPause(), which pauses HTML5 <audio>.
    // Only resume the WebView while audio is actively playing so background timers
    // do not stay alive unnecessarily when idle.
    if (isAudioPlaying) {
      currentWebView?.onResume()
      currentWebView?.resumeTimers()
    }
  }

  override fun onDestroy() {
    isAudioPlaying = false
    runCatching {
      stopService(Intent(this, AudioPlaybackService::class.java))
    }
    currentWebView = null
    super.onDestroy()
  }

  internal fun setAudioPlaying(playing: Boolean, title: String, artist: String) {
    isAudioPlaying = playing
    runCatching {
      val serviceIntent = Intent(this, AudioPlaybackService::class.java).apply {
        putExtra(AudioPlaybackService.EXTRA_TITLE, title)
        putExtra(AudioPlaybackService.EXTRA_ARTIST, artist)
      }
      if (playing) {
        ContextCompat.startForegroundService(this, serviceIntent)
      } else {
        stopService(serviceIntent)
      }
    }
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    currentWebView = webView

    // Harden WebView settings beyond Tauri's defaults.
    webView.settings.apply {
      allowFileAccess = false
      allowContentAccess = false
      setGeolocationEnabled(false)
      javaScriptCanOpenWindowsAutomatically = false
      setSupportMultipleWindows(false)
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      @Suppress("DEPRECATION")
      saveFormData = false
    }
    runCatching {
      val cookies = CookieManager.getInstance()
      cookies.setAcceptCookie(false)
      cookies.setAcceptThirdPartyCookies(webView, false)
      cookies.removeAllCookies(null)
    }

    val contentView = findViewById<android.view.View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
          WindowInsetsCompat.Type.displayCutout() or
          WindowInsetsCompat.Type.ime()
      )
      view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
      WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(contentView)

    webView.addJavascriptInterface(TidingsBridge(this, webView), "TidingsAndroid")

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          webView.evaluateJavascript("(function(){ return Boolean(window.__tidingsBack && window.__tidingsBack()); })()") { result ->
            if (result != "true") {
              isEnabled = false
              onBackPressedDispatcher.onBackPressed()
              isEnabled = true
            }
          }
        }
      }
    )
  }
}
