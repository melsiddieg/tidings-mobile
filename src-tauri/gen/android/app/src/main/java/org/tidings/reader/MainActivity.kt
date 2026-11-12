package org.tidings.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  private var currentWebView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
  }

  override fun onPause() {
    super.onPause()
    // WryActivity.onPause() calls mWebView.onPause(), which pauses HTML5 <audio>.
    // Resume the WebView immediately so audio keeps playing while the phone is locked or in the background.
    currentWebView?.onResume()
    currentWebView?.resumeTimers()
  }

  override fun onDestroy() {
    runCatching {
      stopService(Intent(this, AudioPlaybackService::class.java))
    }
    currentWebView = null
    super.onDestroy()
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    currentWebView = webView

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

    webView.addJavascriptInterface(
      object {
        @JavascriptInterface
        fun openExternal(url: String) {
          val parsed = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
          val scheme = parsed.scheme?.lowercase() ?: return
          if (scheme != "http" && scheme != "https" && scheme != "mailto") return
          runOnUiThread {
            runCatching {
              startActivity(Intent(Intent.ACTION_VIEW, parsed))
            }
          }
        }

        @JavascriptInterface
        fun setAudioPlaybackState(playing: Boolean, title: String, artist: String) {
          runOnUiThread {
            runCatching {
              val serviceIntent = Intent(this@MainActivity, AudioPlaybackService::class.java).apply {
                putExtra(AudioPlaybackService.EXTRA_TITLE, title)
                putExtra(AudioPlaybackService.EXTRA_ARTIST, artist)
              }
              if (playing) {
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
              } else {
                stopService(serviceIntent)
              }
            }
          }
        }
      },
      "TidingsAndroid"
    )

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
