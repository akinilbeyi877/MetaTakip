package com.example.metatakip.controllers.poupsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 📱 Cihaz yeniden başlatıldığında
 * - Foreground servis başlatılır
 * - Çağrı yakalama altyapısı hazır hale gelir
 */
class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BOOT_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "📱 Cihaz açıldı → Arama altyapısı başlatılıyor")

        try {
            // 🔔 Foreground Service başlat
            val serviceIntent = Intent(context, CallForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "✅ CallForegroundService başlatıldı")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Boot sonrası servis başlatılamadı: ${e.message}", e)
        }
    }
}
