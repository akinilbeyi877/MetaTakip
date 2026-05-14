package com.example.metatakip.controllers.poupsms  // KÜÇÜK HARF

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class NotificationService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NOTIFICATION_SERVICE", "✅ Servis oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("NOTIFICATION_SERVICE", "✅ Servis başlatıldı")
        // Arka planda bildirim işlemleri yapın
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("NOTIFICATION_SERVICE", "❌ Servis sonlandırıldı")
        super.onDestroy()
    }
}