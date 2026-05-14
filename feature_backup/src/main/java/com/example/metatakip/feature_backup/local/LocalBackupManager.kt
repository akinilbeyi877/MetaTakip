package com.example.metatakip.feature_backup.local

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupFolderType
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.TimeUtils
import java.io.File

/**
 * 📂 Yerel Yedekleme Yöneticisi
 * Yedek dosyalarını cihazın fiziksel depolama alanında organize eder.
 */
object LocalBackupManager {

    private const val TAG = "LocalBackupManager"

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) Log.w(TAG, "⚠️ Klasör oluşturulamadı: ${dir.absolutePath}")
        }
        return dir
    }

    /**
     * Ana yedek klasörünü döner (Örn: /Downloads/MetaTakip)
     */
    fun getRootFolder(context: Context): File {
        val rootName = BackupPreferences.getBackupFolderName()

        // Android 10+ için Download dizini en güvenli yerdir.
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return ensureDir(File(downloads, rootName))
    }

    fun getTypeFolder(context: Context, type: BackupFolderType): File {
        return ensureDir(File(getRootFolder(context), type.folderName))
    }

    fun getTodayFolder(context: Context, type: BackupFolderType): File {
        val today = TimeUtils.todayFolder()
        return ensureDir(File(getTypeFolder(context, type), today))
    }

    /**
     * 💾 Dosyayı ilgili günün klasörüne kopyalar ve geçici dosyayı temizler.
     */
    fun saveToTodayFolder(
        context: Context,
        sourceFile: File,
        fileName: String,
        type: BackupFolderType
    ): File {
        return try {
            val targetDir = getTodayFolder(context, type)
            val targetFile = File(targetDir, fileName)

            // Dosyayı hedef konuma kopyala
            sourceFile.copyTo(targetFile, overwrite = true)

            // 🧹 Kaynak dosya geçici bir yerdeyse (cache gibi) temizle
            if (sourceFile.exists() && sourceFile.absolutePath != targetFile.absolutePath) {
                sourceFile.delete()
            }

            Log.d(TAG, "✅ Yerel yedek kaydedildi: ${targetFile.name}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Yerel yedek kaydetme hatası: ${e.message}")
            sourceFile // Hata durumunda orijinal dosyayı dön ki işlemler aksamasın
        }
    }

    /**
     * Varsayılan FULL yedekleme için overload.
     */
    fun saveToTodayFolder(context: Context, sourceFile: File, fileName: String): File {
        return saveToTodayFolder(context, sourceFile, fileName, BackupFolderType.FULL)
    }

    fun createTodaySubFolder(context: Context, type: BackupFolderType, subFolderName: String): File {
        return ensureDir(File(getTodayFolder(context, type), subFolderName))
    }

    /**
     * 🧹 ESKİ YEDEKLERİ TEMİZLE
     * Cihazın hafızasını şişirmemek için belirlenen sayıdan fazla yedeği siler.
     * Hibrit sistemde 'INSTANT' yedekler çok sık oluştuğu için bu temizlik şarttır.
     */
    fun deleteOldLocalBackups(context: Context, keepCount: Int = 30) {
        val root = getRootFolder(context)
        if (!root.exists()) return

        try {
            // Tüm alt klasörlerdeki zip ve csv dosyalarını tara
            val allFiles = root.walkTopDown()
                .filter { it.isFile && (it.extension.equals("zip", true) || it.extension.equals("csv", true)) }
                .sortedByDescending { it.lastModified() }
                .toList()

            if (allFiles.size > keepCount) {
                val filesToDelete = allFiles.drop(keepCount)
                filesToDelete.forEach { file ->
                    val deleted = file.delete()
                    if (deleted) Log.d(TAG, "🗑️ Eski yerel yedek silindi: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Temizlik işlemi sırasında hata: ${e.message}")
        }
    }
}