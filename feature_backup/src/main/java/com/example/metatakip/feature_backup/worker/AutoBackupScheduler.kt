package com.example.metatakip.feature_backup.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.metatakip.feature_backup.util.BackupPreferences
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * ⏰ Otomatik Yedekleme Zamanlayıcısı
 * Belirlenen 3 farklı zaman dilimi için yedekleme görevlerini Android sistemine kaydeder.
 */
object AutoBackupScheduler {

    private const val TAG = "AutoBackupScheduler"

    /**
     * 🆔 Cihaz ID'lerini tanıdık isimlere çevirir.
     * Siyah panelde (terminal) kimin planlama yaptığını gösterir.
     */
    fun getDeviceNick(id: String): String {
        return when {
            id.contains("d39e928") -> "REEDER"
            id.contains("cf870d4") -> "SAMSUNG"
            else -> "Terminal-${id.take(4).uppercase()}"
        }
    }

    /**
     * Tüm zaman dilimlerini (slotları) ayarlar.
     */
    fun scheduleAll(context: Context, time1: String, time2: String, time3: String) {
        cancelAll(context) // Çakışmaları önlemek için önce temizle
        scheduleSingle(context, 1, time1)
        scheduleSingle(context, 2, time2)
        scheduleSingle(context, 3, time3)
    }

    /**
     * Tüm aktif zamanlanmış görevleri iptal eder.
     */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("auto_backup_1")
        wm.cancelUniqueWork("auto_backup_2")
        wm.cancelUniqueWork("auto_backup_3")
        Log.d(TAG, "🚫 Tüm otomatik yedekleme görevleri iptal edildi.")
    }

    /**
     * Tek bir zaman dilimi için görevi sisteme enjekte eder.
     */
    private fun scheduleSingle(context: Context, slot: Int, hhmm: String) {
        try {
            val parts = hhmm.split(":")
            if (parts.size != 2) return

            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val now = LocalDateTime.now()
            var nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)

            // Eğer saat geçtiyse bir sonraki güne kur
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }

            val delayMillis = Duration.between(now, nextRun).toMillis()

            // İşleyiciye (Worker) hangi slotun çalıştığını bildiriyoruz
            val inputData = Data.Builder()
                .putInt("slot", slot)
                .putString("scheduled_time", hhmm)
                .build()

            // 🛠️ Kısıtlamalar: İnternet olduğunda ve batarya düşük değilken çalışsın
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints) // Güç ve internet tasarrufu
                .addTag("auto_backup")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "auto_backup_$slot",
                ExistingWorkPolicy.REPLACE, // Mevcut olanı yenisiyle değiştir
                request
            )

            // --- 📢 AKILLI VE DETAYLI LOGLAMA ---
            val deviceId = BackupPreferences.getOrCreateDeviceId()
            val nick = getDeviceNick(deviceId)

            // Siyah panele (terminal) planlama raporunu basıyoruz
            val logMsg = "⏰ PLANLANDI: [$nick] $hhmm saatinde (Slot-$slot) otomatik eşleşecek."
            AutoBackupLogStore.addLog(logMsg)
            Log.d(TAG, logMsg)

        } catch (e: Exception) {
            Log.e(TAG, "❌ scheduleSingle Hatası: ${e.message}")
            AutoBackupLogStore.addLog("❌ HATA: Zaman-$slot kurulamadı (${e.message})")
        }
    }
}