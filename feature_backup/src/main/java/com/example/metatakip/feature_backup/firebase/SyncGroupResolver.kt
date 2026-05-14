package com.example.metatakip.feature_backup.firebase

import android.content.Context
import com.example.metatakip.feature_backup.util.BackupPreferences
import java.util.Locale

/**
 * 🔑 Grup Kimliği Çözücü
 * Cihazların Firebase Firestore üzerinde hangi doküman altında
 * senkronize olacağını belirler.
 */
object SyncGroupResolver {

    /**
     * Google Drive klasör adı ve kullanıcı e-postasına dayalı benzersiz bir grup ID üretir.
     * Bu sayede aynı Drive klasörünü paylaşan cihazlar otomatik olarak aynı gruba dahil olur.
     */
    fun resolve(context: Context): String {
        // 1. Kullanıcının belirlediği klasör adını al (Varsayılan genelde 'MetaTakip')
        val folder = BackupPreferences.getBackupFolderName()

        // 2. Drive e-postasını al (E-posta değişirse grup da değişir, bu bir güvenlik önlemidir)
        val email = BackupPreferences.getDriveEmail().orEmpty()

        // 3. Klasör ve e-postayı birleştirerek ham bir anahtar oluştur
        val raw = if (email.isNotBlank()) {
            "${folder}_${email}"
        } else {
            folder
        }

        // 4. Firestore doküman yolu standartlarına uygun hale getir (Regex temizliği)
        // Sadece küçük harf, rakam ve alt çizgiye izin verilir.
        val resolvedId = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_") // Geçersiz karakterleri '_' yap
            .replace(Regex("_{2,}"), "_")      // Çift alt çizgileri teke indir
            .trim('_')                         // Başta ve sondaki çizgileri sil

        // 5. Boş kalma ihtimaline karşı varsayılan bir değer dön
        return resolvedId.ifBlank { "metatakip_default_group" }
    }
}