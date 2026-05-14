package com.example.metatakip.feature_backup.sync

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 📝 UI Log Yöneticisi
 * Arka plandaki senkronizasyon adımlarını uygulama içindeki 'Log Ekranı'na aktarır.
 */
object UiLogManager {

    private const val TAG = "UiLogManager"
    private const val MAX_LOG_COUNT = 150

    // Thread-safe liste (Aynı anda birden fazla thread log yazabilir)
    private val logs = CopyOnWriteArrayList<String>()

    // Dinleyici (Genelde UI katmanındaki bir Fragment veya Activity)
    @Volatile
    private var listener: ((String) -> Unit)? = null

    /**
     * UI ekranı açıldığında dinleyiciyi bağlar.
     * Zayıf referans yönetimi veya null güvenliği için 'setListener' kritiktir.
     */
    fun setListener(newListener: ((String) -> Unit)?) {
        listener = newListener
        // Dinleyici bağlandığı an mevcut tüm logları gönder
        newListener?.invoke(getAllLogs())
    }

    /**
     * Sisteme yeni bir log satırı ekler.
     */
    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time] $message"

        // Logu listeye ekle
        logs.add(line)

        // Logcat'e de yaz (Hata ayıklama için kolaylık)
        Log.d(TAG, message)

        // Belleği korumak için eski logları temizle
        // CopyOnWriteArrayList'te remove işlemi maliyetli olduğundan kontrolü optimize ediyoruz.
        if (logs.size > MAX_LOG_COUNT) {
            try {
                // Sadece ihtiyaç duyulduğunda ilk elemanı çıkar
                logs.removeAt(0)
            } catch (_: Exception) {}
        }

        // Değişikliği UI'ya bildir
        listener?.invoke(getAllLogs())
    }

    /**
     * Tüm log geçmişini temizler.
     */
    fun clear() {
        logs.clear()
        listener?.invoke("")
    }

    /**
     * Logları alt alta birleştirerek tek bir metin döner.
     */
    fun getAllLogs(): String {
        return if (logs.isEmpty()) {
            "Henüz kayıt yok..."
        } else {
            logs.joinToString(separator = "\n")
        }
    }
}