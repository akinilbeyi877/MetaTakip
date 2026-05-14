package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.OutputStream
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriveBackupManager {
    private const val TAG = "DriveBackupManager"

    fun getDriveService(context: Context): Drive? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                setOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account

            Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("MetaTakip Backup")
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Drive Servisi başlatılamadı: ${e.message}")
            null
        }
    }

    /**
     * ⬇️ Belirli bir dosyayı indirir (ZAMAN AŞIMI KONTROLLÜ)
     * Takılma sorununu çözmek için 45 saniyelik limit eklendi.
     */
    suspend fun downloadFile(driveService: Drive, fileId: String, outputStream: OutputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // ⏱️ 45 Saniye içinde cevap gelmezse işlemi "null" döndürerek iptal et
                val result = withTimeoutOrNull(45000L) {
                    try {
                        val request = driveService.files().get(fileId)
                        // Medya indirme işlemini başlat
                        request.executeMediaAndDownloadTo(outputStream)
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ executeMediaHatası: ${e.message}")
                        false
                    }
                }

                // Eğer result null ise zaman aşımı olmuş demektir
                if (result == null) {
                    Log.e(TAG, "⌛ Drive indirme zaman aşımına uğradı (45sn).")
                    false
                } else {
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ İndirme hatası: ${e.message}")
                false
            } finally {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (e: Exception) {
                    // Kapatma hatasını yutabiliriz
                }
            }
        }
    }

    // --- DİĞER METOTLARIN AYNI KALIYOR (Upload, List, Delete) ---

    fun uploadFile(driveService: Drive, localFile: java.io.File, mimeType: String): String? {
        return try {
            val fileMetadata = File().apply { name = localFile.name }
            val mediaContent = FileContent(mimeType, localFile)
            val file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            file.id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Yükleme hatası: ${e.message}")
            null
        }
    }

    fun listBackupFiles(driveService: Drive): List<File> {
        return try {
            val result = driveService.files().list()
                .setQ("mimeType = 'application/zip' and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, modifiedTime)")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Listeleme hatası: ${e.message}")
            emptyList()
        }
    }

    fun deleteAllBackupsSync(context: Context): Boolean {
        return try {
            val service = getDriveService(context) ?: return false
            val result = service.files().list()
                .setSpaces("drive")
                .setQ("trashed = false and (mimeType = 'application/zip' or name contains 'history.json' or name contains 'lock.json')")
                .execute()

            val files = result.files
            if (files.isNullOrEmpty()) return true

            files.forEach { file ->
                service.files().delete(file.id).execute()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}