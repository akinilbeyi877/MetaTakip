package com.example.metatakip.feature_backup.util

import android.content.Context
import java.io.File

/**
 * 📍 Veritabanı Dosya Yolları
 * Uygulamanın kullandığı ana DB ve SQLite yardımcı dosyalarının (WAL, SHM)
 * fiziksel konumlarını yönetir.
 */
object DbFilePaths {
    const val DB_NAME = "MetaTakip.db"

    /** Ana veritabanı dosyası (.db) */
    fun dbFile(context: Context): File = context.getDatabasePath(DB_NAME)

    /** SQLite Write-Ahead Logging (WAL) dosyası */
    fun walFile(context: Context): File = File(dbFile(context).absolutePath + "-wal")

    /** SQLite Shared Memory (SHM) dosyası */
    fun shmFile(context: Context): File = File(dbFile(context).absolutePath + "-shm")

    /**
     * 🧹 Tüm veritabanı bileşenlerini (DB, WAL, SHM) bir liste olarak döner.
     * Bu metod, yedekleme veya temizlik işlemlerinde tüm dosyaları
     * tek seferde işlemek (silmek veya paketlemek) için hayati önem taşır.
     */
    fun getAllDbFiles(context: Context): List<File> {
        return listOf(
            dbFile(context),
            walFile(context),
            shmFile(context)
        )
    }
}