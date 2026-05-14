package com.example.metatakip.feature_backup.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast

/**
 * 🔄 Uygulama Yeniden Başlatma Yardımcısı
 * Veritabanı geri yükleme (Restore) gibi kritik işlemlerden sonra
 * uygulamanın taze bir başlangıç yapmasını sağlar.
 */
object AppRestartUtil {
    private const val TAG = "AppRestartUtil"

    /**
     * Uygulamayı temiz bir şekilde yeniden başlatır.
     * @param context Uygulama context'i
     * @param delayMs Yeniden başlatmadan önce beklenecek süre (Toast göstermek vb. için)
     */
    fun restartApp(context: Context, delayMs: Long = 1000L) {
        Log.w(TAG, "🔄 Uygulama yeniden başlatılıyor...")

        // 1. Ana ekrana (Launch Intent) yönlendirecek intent'i hazırla
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)?.apply {
            // Tüm önceki aktiviteleri temizle ve yeni bir görev (Task) başlat
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: run {
            Log.e(TAG, "❌ Launch Intent bulunamadı!")
            return
        }

        // 2. Kullanıcıya bilgi ver (UI akışını bozmamak için Handler kullanılır)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                context.startActivity(intent)

                // 3. Mevcut işlemi (Process) tamamen sonlandır
                // Bu adım, SQLite bağlantılarının ve statik değişkenlerin sıfırlanmasını sağlar.
                Process.killProcess(Process.myPid())

                // Sistemin süreci sonlandırdığından emin olmak için (Fallback)
                System.exit(0)
            } catch (e: Exception) {
                Log.e(TAG, "💥 Restart hatası: ${e.message}")
            }
        }, delayMs)
    }

    /**
     * Sadece veritabanı geri yükleme bittiğinde kullanıcıya mesaj gösterip başlatır.
     */
    fun restartAfterRestore(context: Context) {
        Toast.makeText(context, "Veriler başarıyla yüklendi. Uygulama başlatılıyor...", Toast.LENGTH_LONG).show()
        restartApp(context, 2000L)
    }
}