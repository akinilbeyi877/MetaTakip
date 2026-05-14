package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupFolderType
import com.example.metatakip.feature_backup.worker.AutoBackupLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 📥 Drive İndirme Yardımcısı
 * Buluttaki paketlerin kimden geldiğini analiz eder ve ID'leri isimlere (REEDER/SAMSUNG) çevirir.
 * Tam paket ismini siyah panele raporlayarak doğruluğu teyit eder.
 */
object DriveDownloadHelper {
    private const val TAG = "DriveDownloadHelper"

    data class DownloadResult(
        val success: Boolean,
        val message: String,
        val file: File? = null,
        val latest: LatestBackupFinder.LatestBackup? = null
    )

    /**
     * 🆔 Cihaz Kimliği Analizi:
     * Dosya adındaki ID'yi yakalar ve REEDER/SAMSUNG olarak tercüme eder.
     */
    private fun getDeviceIdentity(fileName: String): String {
        return try {
            // Dosya adındaki en sondaki ID kısmını ayıklıyoruz (d39e928c...)
            val deviceId = fileName.substringAfterLast("_").substringBefore(".zip")

            // Sözlüğümüze göre tercüme ediyoruz
            when {
                deviceId.contains("d39e928") -> "REEDER"
                deviceId.contains("cf870d4") -> "SAMSUNG"
                else -> "Terminal-${deviceId.take(4).uppercase()}"
            }
        } catch (e: Exception) {
            "Bilinmeyen Terminal"
        }
    }

    /**
     * Buluttaki en güncel yedeği bulur ve yerel cache dizinine indirir.
     */
    suspend fun downloadLatestBackup(
        context: Context,
        type: BackupFolderType,
        cachePrefix: String = "latest"
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 1. Drive API Servisini Al
            val driveService = DriveBackupManager.getDriveService(context)
                ?: return@withContext DownloadResult(false, "Google Drive bağlantısı yok.")

            // 2. En Güncel Yedeği Ara
            val latest = LatestBackupFinder.findLatestBackup(context, type)
                ?: return@withContext DownloadResult(false, "Bulutta yeni paket bulunamadı.")

            // 3. Yerel Klasör Hazırlığı (cache/backups)
            val backupDir = File(context.cacheDir, "backups").apply {
                if (!exists()) mkdirs()
            }

            // Temizlik (Hafıza dolmaması için eski kalıntıları siler)
            cleanupOldDownloads(backupDir, cachePrefix)

            // Yerel kayıt dosyasını oluştur
            val outFile = File(backupDir, "${cachePrefix}_${latest.fileName}")

            // --- 📢 GELİŞMİŞ TRAFİK RADARI (Siyah Panel Raporu) ---

            val senderNick = getDeviceIdentity(latest.fileName)
            val fullFileName = latest.fileName
            val fileSizeKB = latest.sizeBytes / 1024

            // İstediğin o net ve detaylı yakalama mesajları:
            AutoBackupLogStore.addLog("📥 ALIM: [$senderNick] cihazından veri akışı yakalandı.")
            AutoBackupLogStore.addLog("📦 PAKET: [$fullFileName] ($fileSizeKB KB)")
            AutoBackupLogStore.addLog("⬇️ Paket buluttan yerel hafızaya mühürleniyor...")

            // --- 📥 İNDİRME İŞLEMİ ---
            FileOutputStream(outFile).use { fos ->
                driveService.files().get(latest.fileId)
                    .executeMediaAndDownloadTo(fos)
            }

            // 5. Doğrulama ve Sonuç
            return@withContext if (outFile.exists() && outFile.length() > 0) {
                AutoBackupLogStore.addLog("✅ BAŞARI: [$senderNick] verisi işleme hazır.")
                DownloadResult(true, "Yedek indirildi.", outFile, latest)
            } else {
                AutoBackupLogStore.addLog("❌ HATA: İndirilen dosya boş görünüyor.")
                DownloadResult(false, "Dosya indirildi ancak boş görünüyor.")
            }

        } catch (e: Exception) {
            val errorMsg = "❌ İndirme hatası: ${e.localizedMessage}"
            Log.e(TAG, errorMsg)
            AutoBackupLogStore.addLog(errorMsg)
            DownloadResult(false, e.localizedMessage ?: "Bilinmeyen bir hata oluştu.")
        }
    }

    /**
     * 🧹 Cache dizinindeki eski indirme kalıntılarını temizler.
     */
    private fun cleanupOldDownloads(directory: File, prefix: String) {
        try {
            val files = directory.listFiles { file ->
                file.name.startsWith(prefix) ||
                        file.name.contains("temp_") ||
                        file.name.contains("realtime_pull")
            }
            files?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Temizlik hatası: ${e.message}")
        }
    }

    /**
     * Belirli bir dosya ID'sini doğrudan indirmek için yardımcı metot.
     */
    suspend fun downloadSpecificFile(context: Context, fileId: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext null
            val backupDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
            val outFile = File(backupDir, "manual_$fileName")

            FileOutputStream(outFile).use { fos ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(fos)
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }
}