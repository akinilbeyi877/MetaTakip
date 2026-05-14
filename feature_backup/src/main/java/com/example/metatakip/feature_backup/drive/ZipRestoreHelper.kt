package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.FullBackupManager
import com.example.metatakip.feature_backup.db.MigrationHelper
import com.example.metatakip.feature_backup.worker.AutoBackupLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 🛠️ ZIP Geri Yükleme Yardımcısı
 * İndirilen tam yedek paketlerini mevcut veritabanına entegre eder.
 */
object ZipRestoreHelper {

    private const val TAG = "ZipRestoreHelper"

    /**
     * ⚠️ DİKKAT: Bu işlem mevcut veritabanını tamamen siler ve yedekteki verileri yükler.
     * Hibrit yapıda bu işlemden sonra Trigger ve Index'lerin yeniden kurulması şarttır.
     */
    suspend fun restoreFullZipToActiveDatabase(
        context: Context,
        zipFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 📢 Siyah panele ilk bilgiyi gönderiyoruz
            AutoBackupLogStore.addLog("🔄 ${zipFile.name} paketi açılıyor ve veritabanına mühürleniyor...")
            Log.w(TAG, "🔄 Veritabanı geri yükleme işlemi başlatıldı: ${zipFile.name}")

            // 1. ADIM: Veritabanını ZIP'ten geri yükle
            zipFile.inputStream().use { input ->
                FullBackupManager(context).restoreDbFromZip(input)
            }

            // 📢 İlerleme bilgisi
            AutoBackupLogStore.addLog("📦 Veri kopyalama bitti, hibrit altyapı (Trigger & Index) onarılıyor...")
            Log.d(TAG, "📦 Veri kopyalama bitti, altyapı tazeleniyor...")

            // 2. ADIM: Hibrit Altyapıyı Yenile
            MigrationHelper.checkAndMigrate(context)

            // 📢 Başarı mesajı
            AutoBackupLogStore.addLog("✅ Senkronizasyon Tamamlandı: Veritabanı başarıyla güncellendi.")
            Log.i(TAG, "✅ Geri yükleme ve hibrit yapılandırma başarıyla tamamlandı.")

            true
        }.getOrElse { e ->
            val errorMsg = "💥 Geri yükleme hatası: ${e.message}"
            AutoBackupLogStore.addLog("❌ $errorMsg")
            Log.e(TAG, errorMsg)
            false
        }
    }
}