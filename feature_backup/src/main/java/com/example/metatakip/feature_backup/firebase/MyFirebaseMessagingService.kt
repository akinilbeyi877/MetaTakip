package com.example.metatakip.feature_backup.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.metatakip.feature_backup.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * 🛰️ Firebase Mesajlaşma Servisi
 * Cihazlar arası anlık komutları ve bildirimleri yönetir.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMessaging"
        private const val CHANNEL_ID = "metatakip_realtime_sync"
    }

    /**
     * Cihazın Firebase token'ı değiştiğinde (örn: uygulama ilk kurulum) tetiklenir.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🆕 Yeni FCM Token alındı: $token")
        // Yeni adresi Firestore'daki deftere kaydet
        FirebaseTokenRegistrar.register(applicationContext)
    }

    /**
     * 📩 Bir mesaj geldiğinde tetiklenir.
     * Mesaj bir bildirim mi (Notification) yoksa bir veri komutu mu (Data Payload) kontrol eder.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        // 1. Veri Paketi Kontrolü (Arka plan senkronizasyonu için)
        if (message.data.isNotEmpty()) {
            val type = message.data["type"]
            val senderName = message.data["senderName"] ?: "Başka bir cihaz"

            Log.d(TAG, "📦 Veri paketi alındı. Tip: $type, Gönderen: $senderName")

            // Eğer mesaj tipi 'SYNC' ise BridgeManager zaten SnapshotListener ile
            // veriyi yakalayacaktır. Burası ekstra kontrol noktası olabilir.
        }

        // 2. Görsel Bildirim Kontrolü
        val title = message.notification?.title ?: message.data["title"] ?: "MetaTakip"
        val body = message.notification?.body ?: message.data["body"]

        // Eğer bildirim içeriği boş değilse kullanıcıya göster
        if (!body.isNullOrBlank()) {
            showNotification(title, body)
        }
    }

    /**
     * 🔔 Kullanıcıya standart Android bildirimi gösterir.
     */
    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 ve üzeri için kanal oluşturma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canlı Senkronizasyon Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT // Rahatsız etmemesi için DEFAULT
            ).apply {
                description = "Cihazlar arası veri güncellemeleri hakkında bilgi verir."
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Logonuzu buraya koyun
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Her bildirimin farklı bir ID alması için timestamp kullanıyoruz
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}