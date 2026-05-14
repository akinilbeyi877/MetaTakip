package com.example.metatakip.feature_backup.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 🏛️ Tam Yedekleme Yöneticisi
 * Veritabanını komple ZIP haline getirir ve ZIP'ten geri yükler.
 */
class FullBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "FullBackupManager"
    }

    /**
     * Mevcut veritabanını (DB + WAL + SHM) ZIP paketine dönüştürür.
     */
    fun createBackupZip(): File {
        val dbFile = DbFilePaths.dbFile(context)

        if (!dbFile.exists()) {
            throw IllegalStateException("Veritabanı dosyası bulunamadı: ${dbFile.absolutePath}")
        }

        // 1. ADIM: Verileri diskle eşle (WAL -> DB)
        checkpointWalSafely()

        val wal = DbFilePaths.walFile(context)
        val shm = DbFilePaths.shmFile(context)

        val tempDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val zipFile = File(tempDir, "temp_full_${System.currentTimeMillis()}.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                putEntry(zip, dbFile, DbFilePaths.DB_NAME)
                putEntry(zip, wal, "${DbFilePaths.DB_NAME}-wal")
                putEntry(zip, shm, "${DbFilePaths.DB_NAME}-shm")
            }
            Log.d(TAG, "✅ Tam yedek ZIP oluşturuldu: ${zipFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ZIP oluşturma hatası: ${e.message}")
            throw e
        }

        return zipFile
    }

    /**
     * ⚠️ KRİTİK: ZIP paketinden veritabanını geri yükler.
     * Bu işlemden sonra uygulamanın RESTART edilmesi şarttır.
     */
    fun restoreDbFromZip(zipInput: InputStream) {
        val dbFile = DbFilePaths.dbFile(context)
        val wal = DbFilePaths.walFile(context)
        val shm = DbFilePaths.shmFile(context)

        // 1. ADIM: Mevcut veritabanı bağlantılarını kesinleştirin
        // Not: Bu metod çağrılmadan önce SQLiteOpenHelper kapatılmış olmalı.

        // 2. ADIM: Eski yardımcı dosyaları temizle
        runCatching { wal.delete() }
        runCatching { shm.delete() }

        try {
            ZipInputStream(BufferedInputStream(zipInput)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name

                    val outFile: File? = when (name) {
                        DbFilePaths.DB_NAME -> dbFile
                        "${DbFilePaths.DB_NAME}-wal" -> wal
                        "${DbFilePaths.DB_NAME}-shm" -> shm
                        else -> null
                    }

                    if (outFile != null) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        Log.d(TAG, "✅ Geri yüklendi: $name")
                    }
                    zis.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Restore hatası: ${e.message}")
            throw e
        }
    }

    /**
     * WAL (Write-Ahead Log) dosyasındaki verileri ana dosyaya yazar.
     * Tüm veritabanı işlemleri aynı global kilit altında yapılır ve
     * MetaTakipDb bağlantı havuzu kullanılır (asla close() çağrılmaz).
     */
    private fun checkpointWalSafely() {
        synchronized(MetaTakipDbLock.lock) {
            // Tek bağlantı – okuma/yazma için yeterli, bağlantı havuzdan alınır
            val db = MetaTakipDb.getInstance(context).writableDatabase
            try {
                // FULL checkpoint: tüm WAL verilerini ana DB'ye yazar ve WAL dosyasını sıfırlar
                // PASSIVE yerine FULL tercih edildi, çünkü yedek öncesi tam senkronizasyon isteniyor.
                db.execSQL("PRAGMA wal_checkpoint(FULL)")
                Log.d(TAG, "✅ wal_checkpoint(FULL) başarıyla tamamlandı.")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Checkpoint sırasında hata: ${e.message} (DB kullanımda olabilir, yedek devam ediyor)")
            }
            // ASLA db.close() yapma – bağlantı havuzda kalır.
        }
    }

    private fun putEntry(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        try {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
            Log.d(TAG, "➕ ZIP'e eklendi: $entryName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Giriş eklenemedi: $entryName")
        }
    }
}