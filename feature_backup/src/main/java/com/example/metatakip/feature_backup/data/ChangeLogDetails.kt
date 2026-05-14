package com.example.metatakip.feature_backup.data

import org.json.JSONObject

/**
 * Senkronizasyon paketinin (JSON) içindeki detay verisini temsil eden model.
 */
data class ChangeLogDetails(
    val table: String,

    // 🌍 HİBRİT KRİTİK: Cihazlar arası eşleşme için asıl kimlik
    val uuid: String,

    val recordId: Long,
    val deviceId: String,
    val updatedAt: Long, // ⏱️ Çakışma yönetimi için milisaniye cinsinden zaman damgası

    // Tablodaki kolonların (adSoyad, durum, urun_uuid vb.) tutulduğu yer
    val data: Map<String, Any?>
) {
    /**
     * 🛠️ Bu objeyi ChangeLog içindeki 'details' (String/JSON) alanına
     * dönüştürmek için yardımcı metot.
     */
    fun toJsonString(): String {
        return JSONObject().apply {
            put("table", table)
            put("uuid", uuid)
            put("recordId", recordId)
            put("deviceId", deviceId)
            put("updatedAt", updatedAt)

            val dataObj = JSONObject()
            data.forEach { (key, value) ->
                dataObj.put(key, value ?: JSONObject.NULL)
            }
            put("data", dataObj)
        }.toString()
    }
}