package com.example.metatakip.controllers.poupsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.example.metatakip.R // ✅ BU IMPORTU EKLEYİN

class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CALL_SERVICE"
        private const val NOTIFICATION_CHANNEL_ID = "call_monitor_channel"
        private const val NOTIFICATION_ID = 101

        fun start(context: Context) {
            try {
                val intent = Intent(context, CallForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                    Log.d(TAG, "✅ Foreground Service başlatıldı (API 26+)")
                } else {
                    context.startService(intent)
                    Log.d(TAG, "✅ Service başlatıldı (API 26-)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Service başlatma hatası: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "📞 Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📞 Service onStartCommand")

        try {
            // Notification oluştur
            createNotification()

            Log.d(TAG, "✅ Foreground Service aktif")
            Toast.makeText(this, "Arama takibi aktif", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Service başlatma hatası", e)
        }

        return START_STICKY
    }

    private fun createNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Arama Takip Servisi",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Gelen aramaları takip ediyor"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                Log.d(TAG, "✅ Notification kanalı oluşturuldu")
            }

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }.apply {
                setContentTitle("MetaTakip")
                setContentText("Arama takibi aktif")
                setSmallIcon(R.drawable.ic_launcher_foreground) // ✅ R referansı burada
                setOngoing(true)
                setAutoCancel(false)
                setPriority(Notification.PRIORITY_LOW)
            }.build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ Foreground Notification gösteriliyor")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Notification oluşturma hatası", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "📞 Service onDestroy")
        Toast.makeText(this, "Arama takibi durduruldu", Toast.LENGTH_SHORT).show()
    }
}

// ToastHelper sınıfını kaldırdık çünkü artık gerek yok