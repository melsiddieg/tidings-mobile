package org.tidings.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AudioPlaybackService : Service() {
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    acquireLocks()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Playing episode"
    val artist = intent?.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() } ?: "Tidings"

    val notification = buildNotification(title, artist)
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceCompat.startForeground(
          this,
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    }
    acquireLocks()
    return START_STICKY
  }

  override fun onDestroy() {
    releaseLocks()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun acquireLocks() {
    runCatching {
      if (wakeLock == null) {
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tidings:AudioPlaybackWakeLock").apply {
          setReferenceCounted(false)
        }
      }
      if (wakeLock?.isHeld == false) {
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
      }
    }
    runCatching {
      if (wifiLock == null) {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
          @Suppress("DEPRECATION")
          WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm.createWifiLock(lockMode, "tidings:AudioPlaybackWifiLock").apply {
          setReferenceCounted(false)
        }
      }
      if (wifiLock?.isHeld == false) {
        wifiLock?.acquire()
      }
    }
  }

  private fun releaseLocks() {
    runCatching {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
      wakeLock = null
    }
    runCatching {
      if (wifiLock?.isHeld == true) {
        wifiLock?.release()
      }
      wifiLock = null
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Audio Playback",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Keeps podcast audio playing while the screen is locked"
        setShowBadge(false)
      }
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(title: String, artist: String): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(artist)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  companion object {
    const val CHANNEL_ID = "tidings_audio_playback"
    const val NOTIFICATION_ID = 4201
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_ARTIST = "extra_artist"
  }
}
