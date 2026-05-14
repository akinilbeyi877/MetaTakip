package com.example.metatakip.feature_backup.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 📂 Dosya Açma ve Paylaşma Yardımcısı
 * Üretilen yedek dosyalarının (CSV/ZIP) harici uygulamalar tarafından
 * görüntülenmesini sağlar.
 */
object FileOpenUtil {
    private const val TAG = "FileOpenUtil"

    /**
     * Belirtilen dosyayı uygun bir uygulama ile açar.
     * @param context Uygulama context'i
     * @param file Açılacak fiziksel dosya
     */
    fun openFile(context: Context, file: File) {
        // 1. Dosya varlık kontrolü (Kritik: Dosya silinmişse uygulama çökmesin)
        if (!file.exists()) {
            Toast.makeText(context, "Dosya bulunamadı!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ Açılmaya çalışılan dosya mevcut değil: ${file.absolutePath}")
            return
        }

        try {
            // 2. FileProvider ile güvenli URI oluşturma
            // Android 7.0+ 'file://' protokolü yasak olduğu için 'content://' kullanıyoruz.
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // 3. MIME Tipi Belirleme
            val mime = when {
                file.name.endsWith(".csv", true) -> "text/comma-separated-values" // Daha spesifik MIME
                file.name.endsWith(".zip", true) -> "application/zip"
                file.name.endsWith(".txt", true) -> "text/plain"
                else -> "*/*"
            }

            // 4. Intent Hazırlama
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Diğer uygulamaya okuma izni ver
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 5. Uygun Uygulama Kontrolü (Chooser)
            // Kullanıcıya dosyayı hangi uygulama ile açmak istediğini soran bir pencere açar.
            val chooser = Intent.createChooser(intent, "Dosyayı aç:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooser)

        } catch (e: Exception) {
            Log.e(TAG, "💥 Dosya açılırken hata: ${e.message}")
            Toast.makeText(context, "Bu dosya tipini açacak uygulama bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }
}