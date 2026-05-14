package com.example.metatakip.feature_backup.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.UUID

/**
 * ⚙️ Yedekleme ve Senkronizasyon Tercihleri
 * Uygulama ayarlarını ve cihaz kimliğini kalıcı olarak saklar.
 */
object BackupPreferences {

    private const val TAG = "BackupPreferences"
    private const val PREF_NAME = "backup_prefs"

    // Key tanımlamaları
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_BACKUP_FOLDER_NAME = "backup_folder_name"
    private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
    private const val KEY_DRIVE_CONNECTED = "drive_connected"
    private const val KEY_DRIVE_EMAIL = "drive_email"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private const val KEY_AUTO_TIME_1 = "auto_time_1"
    private const val KEY_AUTO_TIME_2 = "auto_time_2"
    private const val KEY_AUTO_TIME_3 = "auto_time_3"
    private const val KEY_LAST_SYNC_DEVICE    = "last_sync_device"
    private const val KEY_LAST_RESTORE_FILE   = "last_restore_file"
    private const val KEY_LAST_RESTORE_EMAIL  = "last_restore_email"
    private const val KEY_LAST_RESTORE_FOLDER = "last_restore_folder"

    // 🔧 YENİ: Drive ana klasör ID'si — duplikat klasör sorununu çözer
    // findOrCreateFolder() ad araması yapar ve Drive'da aynı isimde birden fazla
    // klasör varsa her cihaz farklı birini alabilir. Bulunan/oluşturulan ID
    // kaydedilerek sonraki yüklemelerde ad araması atlanır, hep aynı klasör kullanılır.
    private const val KEY_DRIVE_ROOT_FOLDER_ID = "drive_root_folder_id"

    // 🔥 Global context referansı (Application context ile initialize edilmeli)
    @Volatile
    private var appContext: Context? = null

    /**
     * 🔥 Uygulama context'ini set et (Application sınıfında çağrılmalı)
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "✅ BackupPreferences initialized")
    }

    private fun getContext(): Context {
        return appContext ?: throw IllegalStateException("BackupPreferences not initialized. Call initialize() first.")
    }

    private fun prefs() = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // --- KLASÖR AYARLARI ---

    fun setBackupFolderName(folderName: String) {
        val name = folderName.trim().ifBlank { "MetaTakip Yedek" }
        prefs().edit().putString(KEY_BACKUP_FOLDER_NAME, name).apply()
    }

    fun getBackupFolderName(): String {
        return prefs().getString(KEY_BACKUP_FOLDER_NAME, "MetaTakip Yedek") ?: "MetaTakip Yedek"
    }

    fun setBackupFolderUri(uri: String?) {
        prefs().edit().putString(KEY_BACKUP_FOLDER_URI, uri).apply()
    }

    fun getBackupFolderUri(): String? = prefs().getString(KEY_BACKUP_FOLDER_URI, null)

    fun clearBackupFolderUri() {
        prefs().edit().remove(KEY_BACKUP_FOLDER_URI).apply()
    }

    // --- 🔧 YENİ: DRIVE ANA KLASÖR ID YÖNETİMİ ---

    /**
     * Drive'da bulunan veya oluşturulan ana (root) klasörün ID'sini kaydet.
     * Bu sayede aynı isimde birden fazla klasör olsa bile hep aynısı kullanılır.
     */
    fun setDriveRootFolderId(folderId: String) {
        prefs().edit().putString(KEY_DRIVE_ROOT_FOLDER_ID, folderId).apply()
        Log.d(TAG, "📁 Drive root klasör ID kaydedildi: $folderId")
    }

    /**
     * Kaydedilmiş Drive ana klasör ID'sini döner.
     * null dönerse findOrCreateFolder() ile yeniden arama yapılmalı.
     */
    fun getDriveRootFolderId(): String? = prefs().getString(KEY_DRIVE_ROOT_FOLDER_ID, null)

    /**
     * Drive bağlantısı kesildiğinde veya klasör adı değiştiğinde ID'yi temizle.
     */
    fun clearDriveRootFolderId() {
        prefs().edit().remove(KEY_DRIVE_ROOT_FOLDER_ID).apply()
        Log.d(TAG, "🗑️ Drive root klasör ID temizlendi")
    }

    // --- DRIVE BAĞLANTI AYARLARI ---

    fun setDriveConnected(connected: Boolean) {
        prefs().edit().putBoolean(KEY_DRIVE_CONNECTED, connected).apply()
        if (!connected) {
            setDriveEmail(null)
            clearDriveRootFolderId() // 🔧 YENİ: Bağlantı kopunca kaydedilmiş klasör ID'sini de temizle
        }
    }

    fun isDriveConnected(): Boolean = prefs().getBoolean(KEY_DRIVE_CONNECTED, false)

    fun setDriveEmail(email: String?) {
        prefs().edit().putString(KEY_DRIVE_EMAIL, email?.lowercase()?.trim()).apply()
        // E-posta değişince eski klasör ID'si geçersizleşebilir
        if (email == null) clearDriveRootFolderId()
    }

    fun getDriveEmail(): String? = prefs().getString(KEY_DRIVE_EMAIL, null)

    // --- OTOMATİK YEDEKLEME ZAMANLAMASI ---

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun isAutoBackupEnabled(): Boolean = prefs().getBoolean(KEY_AUTO_BACKUP_ENABLED, false)

    fun setLastBackupTime(timeMillis: Long) {
        prefs().edit().putLong(KEY_LAST_BACKUP_TIME, timeMillis).apply()
    }

    fun getLastBackupTime(): Long = prefs().getLong(KEY_LAST_BACKUP_TIME, 0L)

    fun setAutoBackupTime1(value: String) { prefs().edit().putString(KEY_AUTO_TIME_1, value).apply() }
    fun getAutoBackupTime1(): String = prefs().getString(KEY_AUTO_TIME_1, "09:00") ?: "09:00"

    fun setAutoBackupTime2(value: String) { prefs().edit().putString(KEY_AUTO_TIME_2, value).apply() }
    fun getAutoBackupTime2(): String = prefs().getString(KEY_AUTO_TIME_2, "14:00") ?: "14:00"

    fun setAutoBackupTime3(value: String) { prefs().edit().putString(KEY_AUTO_TIME_3, value).apply() }
    fun getAutoBackupTime3(): String = prefs().getString(KEY_AUTO_TIME_3, "21:00") ?: "21:00"

    /**
     * Google Sign-In üzerinden gerçek zamanlı Drive bağlantısını sorgular.
     * SharedPreferences'taki önbelleğe değil, anlık hesap durumuna bakar.
     * Bağlantı durumu değişmişse önbelleği de günceller.
     */
    fun isActuallyDriveConnected(): Boolean {
        return try {
            // Scope kontrolü YAPMIYOR — scope uyuşmazlığı yanlış false döndürür.
            // Sadece hesap var mı yok mu bakıyoruz.
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn
                .getLastSignedInAccount(getContext())
            val connected = account != null
            if (!connected && isDriveConnected()) {
                prefs().edit()
                    .putBoolean(KEY_DRIVE_CONNECTED, false)
                    .remove(KEY_DRIVE_EMAIL)
                    .apply()
            }
            connected
        } catch (e: Exception) {
            isDriveConnected()
        }
    }

    /**
     * Şu an oturum açık Google hesabının e-postasını döner.
     * Oturum yoksa null döner.
     */
    fun getActualDriveEmail(): String? {
        return try {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getLastSignedInAccount(getContext())?.email
        } catch (e: Exception) {
            getDriveEmail()
        }
    }

    fun setLastSyncDevice(deviceName: String) {
        prefs().edit().putString(KEY_LAST_SYNC_DEVICE, deviceName).apply()
    }
    fun getLastSyncDevice(): String = prefs().getString(KEY_LAST_SYNC_DEVICE, "") ?: ""

    /** Son başarıyla restore edilen yedeğin dosya adını tam olarak kaydet. */
    fun setLastRestoreFile(fileName: String) {
        prefs().edit().putString(KEY_LAST_RESTORE_FILE, fileName.trim()).apply()
    }
    /** Son restore edilen yedeğin dosya adını döner. Hiç yapılmamışsa boş string. */
    fun getLastRestoreFile(): String = prefs().getString(KEY_LAST_RESTORE_FILE, "") ?: ""

    /** Restore işleminde kullanılan Gmail hesabını kaydet. */
    fun setLastRestoreEmail(email: String) {
        prefs().edit().putString(KEY_LAST_RESTORE_EMAIL, email.trim().lowercase()).apply()
    }
    /** Restore sırasında aktif olan Gmail hesabını döner. */
    fun getLastRestoreEmail(): String = prefs().getString(KEY_LAST_RESTORE_EMAIL, "") ?: ""

    /** Restore sırasında indirilen Drive klasör adını kaydet. */
    fun setLastRestoreFolder(folderName: String) {
        prefs().edit().putString(KEY_LAST_RESTORE_FOLDER, folderName.trim()).apply()
    }
    /** Son restore'da kullanılan Drive klasör adını döner. */
    fun getLastRestoreFolder(): String = prefs().getString(KEY_LAST_RESTORE_FOLDER, "") ?: ""

    // --- 🆔 CİHAZ KİMLİĞİ VE İSİMLENDİRME (KRİTİK) ---

    /**
     * Cihaz için benzersiz bir senkronizasyon ID'si üretir veya mevcut olanı döner.
     * Hibrit mimaride 'record_id' çakışmalarını bu ID ile yönetiyoruz.
     */
    fun getOrCreateDeviceId(): String {
        val current = prefs().getString(KEY_DEVICE_ID, null)
        if (!current.isNullOrBlank()) return current

        // ANDROID_ID fabrika ayarlarına dönülene kadar sabittir.
        val androidId = runCatching {
            Settings.Secure.getString(getContext().contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val generated = when {
            !androidId.isNullOrBlank() -> "dev_$androidId"
            else -> "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        }

        prefs().edit().putString(KEY_DEVICE_ID, generated).apply()
        Log.i(TAG, "🆕 Yeni Cihaz ID Üretildi: $generated")
        return generated
    }

    fun setDeviceName(value: String) {
        prefs().edit().putString(KEY_DEVICE_NAME, value.trim()).apply()
    }

    fun getDeviceName(): String {
        val saved = prefs().getString(KEY_DEVICE_NAME, null)?.trim()
        if (!saved.isNullOrBlank()) return saved
        return buildDefaultDeviceName()
    }

    /**
     * UI'da gösterilecek etiket: "Samsung-S21 (A1B2C3)"
     */
    fun getDeviceLabel(): String {
        val name = getDeviceName()
        val id = getOrCreateDeviceId()
        val shortId = if (id.length > 6) id.takeLast(6).uppercase() else id.uppercase()
        return "$name ($shortId)"
    }

    private fun buildDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: ""
        val base = "$manufacturer $model".trim()

        return if (base.isNotBlank()) {
            base.replace(" ", "_")
        } else {
            "Android_Device"
        }
    }
}
