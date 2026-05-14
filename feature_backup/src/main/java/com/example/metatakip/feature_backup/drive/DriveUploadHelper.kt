package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupFolderType
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.TimeUtils
import com.example.metatakip.feature_backup.worker.AutoBackupLogStore
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as LocalFile

/**
 * 🚀 Drive Yükleme Yardımcısı
 * Yerel paketleri Google Drive hiyerarşisine uygun şekilde yükler ve detaylı log üretir.
 */
object DriveUploadHelper {

    private const val TAG = "DriveUploadHelper"

    /**
     * Yerel bir dosyayı Drive'daki ilgili klasöre yükler.
     * Cihaz ismini dosya adına mühürleyerek diğer terminallerin tanımasını sağlar.
     */
    suspend fun uploadToDrive(
        context: Context,
        localFile: LocalFile,
        type: BackupFolderType
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Drive Servis Kontrolü
            val driveService = DriveBackupManager.getDriveService(context)
                ?: return@withContext false

            val rootFolderName = BackupPreferences.getBackupFolderName()
            val today = TimeUtils.todayFolder()
            val deviceId = BackupPreferences.getOrCreateDeviceId()
            val deviceName = BackupPreferences.getDeviceName().uppercase()

            // 2. Klasör Hiyerarşisi (Root -> Tip -> Tarih)
            // 🔧 DÜZELTME: Root klasör için önce kaydedilmiş ID'yi dene.
            // Drive'da aynı isimde birden fazla klasör varsa firstOrNull() farklı
            // sonuçlar döndürerek her cihazın farklı klasöre yüklemesine neden olur.
            // Kaydedilmiş ID varsa ve hâlâ geçerliyse doğrudan kullan — isim araması yapma.
            val rootId = resolveRootFolderId(driveService, rootFolderName)

            val typeId = findOrCreateFolder(driveService, type.folderName, rootId)
            val todayId = findOrCreateFolder(driveService, today, typeId)

            // 3. Dosya İsmi ve Tür Tanımlama
            val fileDescription = if (type == BackupFolderType.FULL) "Tam Yedek" else "Anlık Paket"

            // 💡 KRİTİK DEĞİŞİKLİK: Dosya adına cihaz ismini ekliyoruz (Örn: realtime_REEDER_d39e928.zip)
            val remoteFileName = if (!localFile.name.contains(deviceId)) {
                "${localFile.nameWithoutExtension}_${deviceName}_$deviceId.${localFile.extension}"
            } else {
                localFile.name
            }

            val meta = File().apply {
                name = remoteFileName
                parents = listOf(todayId)
            }

            // 4. Medya İçeriği Hazırlama
            val mimeType = when (localFile.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                else -> "application/octet-stream"
            }

            // 🔍 TANILAMA: Yüklenmeden önce dosya durumunu kesin olarak logla
            val localSizeBytes = localFile.length()
            val localExists = localFile.exists()
            val localIsFile = localFile.isFile
            Log.d(TAG, "📏 UPLOAD GİRİŞ BOYUTU: $localSizeBytes byte | exists=$localExists | isFile=$localIsFile | path=${localFile.absolutePath}")
            AutoBackupLogStore.addLog("📏 UPLOAD GİRİŞ: $localSizeBytes byte | exists=$localExists | isFile=$localIsFile")

            if (!localExists || !localIsFile) {
                AutoBackupLogStore.addLog("❌ Dosya bulunamadı veya klasör: ${localFile.absolutePath}")
                return@withContext false
            }
            if (localSizeBytes < 100) {
                AutoBackupLogStore.addLog("❌ Dosya çok küçük ($localSizeBytes byte) — yükleme iptal")
                return@withContext false
            }

            val media = FileContent(mimeType, localFile)

            // ✍️ 5. DETAYLI LOGLAMA: Siyah panele gönderim raporu bas
            AutoBackupLogStore.addLog("⬆️ GÖNDERİM: [$deviceName] $fileDescription hazırlanıyor...")

            val uploadedFile = driveService.files()
                .create(meta, media)
                .setFields("id,name,parents")
                .execute()

            if (uploadedFile != null && uploadedFile.id.isNotEmpty()) {
                Log.d(TAG, "✅ Yükleme başarılı: ${uploadedFile.id}")
                AutoBackupLogStore.addLog("✅ BAŞARI: [$deviceName] paketi buluta mühürledi.")
                true
            } else {
                false
            }

        }.getOrElse { e ->
            val errorMsg = "❌ Yükleme Hatası: ${e.localizedMessage}"
            Log.e(TAG, errorMsg)
            AutoBackupLogStore.addLog(errorMsg)
            false
        }
    }

    suspend fun uploadToDrive(context: Context, localFile: LocalFile): Boolean {
        return uploadToDrive(context, localFile, BackupFolderType.FULL)
    }

    /**
     * 🔧 YENİ: Drive ana (root) klasör ID'sini çözer.
     *
     * Öncelik sırası:
     *  1. BackupPreferences'te kaydedilmiş ID → Drive'da hâlâ varsa doğrudan kullan
     *  2. Yoksa veya silinmişse → isim araması yap, bulduğunu kaydet
     *  3. Hiç bulunamazsa → yeni klasör oluştur, ID'sini kaydet
     *
     * Bu sayede Drive'da "MetaTakip Yedek01" isimli 3 ayrı klasör olsa bile
     * her cihaz ilk bağlandığında hangi klasörü bulduysa (veya oluşturduysun)
     * hep o klasörü kullanır — farklı klasörlere yükleme sorunu ortadan kalkar.
     */
    private fun resolveRootFolderId(driveService: Drive, folderName: String): String {
        val savedId = BackupPreferences.getDriveRootFolderId()

        if (!savedId.isNullOrBlank()) {
            val stillExists = try {
                val f = driveService.files().get(savedId).setFields("id,trashed").execute()
                f != null && f.trashed != true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Kaydedilmiş root ID artık geçersiz, yeniden aranıyor: $savedId")
                false
            }

            if (stillExists) {
                Log.d(TAG, "📁 Root klasör ID önbellekten alındı: $savedId")
                return savedId
            }

            // Eski ID geçersiz — temizle ve yeniden ara
            BackupPreferences.clearDriveRootFolderId()
        }

        // İlk bağlantı veya ID geçersizleşmişse: isim araması yap
        val newId = findOrCreateFolder(driveService, folderName, null)
        BackupPreferences.setDriveRootFolderId(newId)
        Log.d(TAG, "📁 Root klasör ID kaydedildi: $newId  (klasör: '$folderName')")
        return newId
    }

    /**
     * 📁 Drive üzerinde klasör arar veya yoksa oluşturur.
     *
     * NOT: Root klasör için doğrudan çağırmak yerine resolveRootFolderId() kullanın.
     * Bu fonksiyon alt klasörler (tip, tarih) için kullanılmaya devam eder.
     */
    fun findOrCreateFolder(driveService: Drive, folderName: String, parentId: String?): String {
        return try {
            val escaped = folderName.replace("'", "\\'")
            val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
            val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and name = '$escaped'$parentClause"

            val existing = driveService.files()
                .list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id,name)")
                .execute()
                .files
                ?.firstOrNull()

            if (existing != null) return existing.id

            val folderMeta = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) parents = listOf(parentId)
            }

            val newFolder = driveService.files().create(folderMeta).setFields("id").execute()
            newFolder.id
        } catch (e: Exception) {
            throw e
        }
    }
}
