package com.example.metatakip.feature_backup.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.util.DbFilePaths
import com.example.metatakip.feature_backup.util.MetaTakipDbLock
import com.example.metatakip.feature_data.db.MetaTakipDb
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SnapshotManager {
    private const val TAG = "SnapshotManager"

    fun createSnapshot(context: Context): File {
        val dbFile = DbFilePaths.dbFile(context)

        if (!dbFile.exists()) {
            throw IllegalStateException("🚨 Kritik Hata: Veritabanı dosyası bulunamadı: ${dbFile.absolutePath}")
        }

        // 1. ADIM: WAL Checkpoint (global kilit + MetaTakipDb bağlantısı)
        synchronized(MetaTakipDbLock.lock) {
            val db = MetaTakipDb.getInstance(context).writableDatabase
            try {
                db.execSQL("PRAGMA wal_checkpoint(FULL)")
                Log.d(TAG, "✅ WAL Checkpoint başarılı.")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Checkpoint sırasında uyarı (DB meşgul olabilir): ${e.message}")
            }
            // ASLA db.close() yapma – bağlantı havuzda kalır
        }

        // 2. ADIM: Klasör Hazırlığı
        val backupDir = File(context.cacheDir, "snapshots").apply {
            if (!exists()) mkdirs()
        }

        cleanupOldSnapshots(backupDir)

        val zipFile = File(backupDir, "snap_${System.currentTimeMillis()}.zip")

        // 3. ADIM: ZIP Paketleme
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                addIfExists(zip, dbFile, DbFilePaths.DB_NAME)
                val wal = DbFilePaths.walFile(context)
                val shm = DbFilePaths.shmFile(context)
                if (wal.exists()) addIfExists(zip, wal, "${DbFilePaths.DB_NAME}-wal")
                if (shm.exists()) addIfExists(zip, shm, "${DbFilePaths.DB_NAME}-shm")
            }
            Log.d(TAG, "📦 Snapshot oluşturuldu: ${zipFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ZIP oluşturma hatası: ${e.message}")
            throw e
        }

        return zipFile
    }

    private fun addIfExists(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        try {
            zip.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Dosya eklenemedi ($entryName): ${e.message}")
        }
    }

    private fun cleanupOldSnapshots(dir: File) {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}