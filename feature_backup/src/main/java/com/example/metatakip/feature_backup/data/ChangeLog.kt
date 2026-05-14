package com.example.metatakip.feature_backup.data

import org.json.JSONObject

/**
 * Veritabanındaki değişiklikleri takip eden model sınıfı
 * Her INSERT, UPDATE, DELETE işlemi buraya kaydedilir
 */
data class ChangeLog(
    val id: Long = 0,
    val tableName: String,           // Hangi tablo (musteri, siparis, etc)
    val actionType: ActionType,      // INSERT, UPDATE, DELETE
    val recordId: Long,              // Değişen kaydın yerel ID'si
    val changedAt: Long,             // Değişiklik zamanı (timestamp)
    val userId: Int = 0,             // Hangi kullanıcı yaptı (opsiyonel)
    val details: String? = null,     // Detaylı bilgi (JSON formatında UUID ve tüm veriler)
    var synced: Boolean = false      // Yedekleme senkronize edildi mi?
) {
    enum class ActionType {
        INSERT,
        UPDATE,
        DELETE
    }

    /**
     * 🌍 Hibrit Yapı İçin: Details içindeki küresel UUID'yi çeker.
     * Bu sayede log ekranında hangi Ahmet'in işlendiğini UUID'den anlarız.
     */
    fun getUuidFromDetails(): String {
        return try {
            if (details.isNullOrEmpty()) ""
            else JSONObject(details).optString("uuid", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * ⏱️ Hibrit Yapı İçin: Details içindeki updatedAt bilgisini çeker.
     */
    fun getUpdatedAtFromDetails(): Long {
        return try {
            if (details.isNullOrEmpty()) 0L
            else JSONObject(details).optLong("updatedAt", 0L)
        } catch (e: Exception) {
            0L
        }
    }

    // Ekran için formatlı açıklama
    fun getDisplayMessage(): String {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(changedAt * 1000)) // Değişiklik zamanı

        val actionIcon = when (actionType) {
            ActionType.INSERT -> "🟢"
            ActionType.UPDATE -> "🔄"
            ActionType.DELETE -> "🔴"
        }

        val tableNameTr = when (tableName) {
            "musteri" -> "Müşteri"
            "siparis" -> "Sipariş"
            "firma" -> "Firma"
            "urun" -> "Ürün"
            "urun_tipi" -> "Ürün Tipi"
            "personel" -> "Personel"
            "mesaj_sablon" -> "Mesaj Şablonu"
            "etiket_sablon" -> "Etiket Şablonu"
            else -> tableName
        }

        val actionTr = when (actionType) {
            ActionType.INSERT -> "Eklendi"
            ActionType.UPDATE -> "Güncellendi"
            ActionType.DELETE -> "Silindi"
        }

        val uuidShort = getUuidFromDetails().takeLast(6) // UUID'nin son 6 hanesi
        val uuidSuffix = if (uuidShort.isNotEmpty()) " [UUID: ..$uuidShort]" else ""

        return "$actionIcon $timeStr - $tableNameTr $actionTr$uuidSuffix"
    }

    // Detaylı bilgi için
    fun getDetailedMessage(): String {
        val base = getDisplayMessage()
        return if (details != null) "$base\n   📝 Detay: $details" else base
    }
}