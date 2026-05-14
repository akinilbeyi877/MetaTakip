package com.example.metatakip.feature_backup.worker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.drive.*
import com.example.metatakip.feature_backup.util.*
import com.example.metatakip.feature_data.db.MetaTakipDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🛰️ METATAKİP GÜVENLİ SENKRONİZASYON İŞÇİSİ
 * Bu sınıf; kilit yönetimi, veri indirme, akıllı birleştirme ve
 * detaylı içerik raporlaması ile buluta yükleme süreçlerini yönetir.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val TAG = "BackupWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val myDevice = BackupPreferences.getDeviceName()
        var lockAcquired = false

        try {
            AutoBackupLogStore.addLog("⏰ Senkronizasyon Başladı [$myDevice]")

            // 1. ADIM: Kilit Yönetimi
            AutoBackupLogStore.addLog("🔐 Bulut erişim kilidi kontrol ediliyor...")
            val lockResult = DriveLockManager.acquireLock(
                context = context,
                action = "backup",
                onStatus = { status -> AutoBackupLogStore.addLog("⏳ $status") }
            )

            if (!lockResult.acquired) {
                AutoBackupLogStore.addLog("⚠️ Bulut meşgul: ${lockResult.reason}")
                return@withContext Result.retry()
            }
            lockAcquired = true

            // 2. ADIM: Yerel Değişiklik Analizi
            // NOT: Değişiklikler change_log tablosuna DB trigger ile yazılır.
            // Trigger henüz commit olmamış olabileceğinden kısa bir bekleme eklenmiştir.
            kotlinx.coroutines.delay(500)
            val unsyncedChanges = ChangeLogManager.getUnsyncedChanges(context)

            if (unsyncedChanges.isNotEmpty()) {
                val summary = unsyncedChanges.groupBy { it.tableName }
                    .map { "${it.key}: ${it.value.size}" }
                    .joinToString(", ")
                AutoBackupLogStore.addLog("📦 Paket İçeriği [$myDevice]: $summary")
            } else {
                // Gönderilecek değişiklik yok — indirme/restore YAPMA.
                // Uzaktan veri alma işlemi FirebaseRealtimeBridgeManager.applyRemoteEvent() tarafından yapılır.
                // BackupWorker sadece YUKLER, asla indirip yerel DB'yi ezmez.
                AutoBackupLogStore.addLog("ℹ️ Gönderilecek yeni veri yok, işlem atlanıyor.")
                return@withContext Result.success()
            }

            // 3. ADIM: Paketi Drive'a Gönder (sadece değişiklik varsa)
            val finalZip = FullBackupManager(context).createBackupZip()
            val uploaded = DriveUploadHelper.uploadToDrive(context, finalZip, BackupFolderType.FULL)

            if (uploaded) {
                val ids = unsyncedChanges.map { it.id }
                if (ids.isNotEmpty()) {
                    ChangeLogManager.markAsSynced(context, ids)
                }
                AutoBackupLogStore.addLog("✅ Senkronizasyon Tamamlandı: Tüm terminaller eşitlendi.")
            }

            Result.success()

        } catch (e: Exception) {
            val errorMsg = "❌ Kritik Hata [$myDevice]: ${e.localizedMessage}"
            Log.e(TAG, errorMsg)
            AutoBackupLogStore.addLog(errorMsg)
            Result.retry()
        } finally {
            if (lockAcquired) {
                DriveLockManager.releaseLock(context)
                AutoBackupLogStore.addLog("🔓 Bulut kilidi serbest bırakıldı.")
            }
        }
    }

    /**
     * 🔧 DÜZELTİLDİ: ChangeLog listesini veritabanına işler.
     * Artık global kilit kullanılır, doğrudan MetaTakipDb bağlantısı alınır,
     * asla close() çağrılmaz.
     */
    private suspend fun mergeUnsyncedChanges(context: Context, changes: List<ChangeLog>): Boolean {
        var successCount = 0
        return try {
            synchronized(MetaTakipDbLock.lock) {
                val db = MetaTakipDb.getInstance(context).writableDatabase
                val manager = PartialBackupManager(context)

                db.beginTransaction()
                try {
                    changes.forEach { change ->
                        if (manager.applySingleChangeLogToDb(db, change)) {
                            successCount++
                        }
                    }
                    db.setTransactionSuccessful()
                    AutoBackupLogStore.addMergeLog(successCount, 0)
                    true
                } finally {
                    db.endTransaction()
                    // ASLA db.close() yapma – bağlantı havuzda kalır
                }
            }
        } catch (e: Exception) {
            AutoBackupLogStore.addLog("💥 Veri birleştirme hatası: ${e.message}")
            false
        }
    }
}