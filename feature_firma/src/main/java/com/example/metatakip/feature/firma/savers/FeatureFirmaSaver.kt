package com.example.metatakip.feature.firma.savers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.entityModel.Firma
import dao.MetaTakipFirmaDao

/**
 * 🏢 FeatureFirmaSaver
 * Firma verilerinin veritabanına güvenli şekilde yazılmasını sağlar.
 * Duplicate (Çiftleme) koruması için GenericFormSaver ile uyumlu çalışır.
 */
object FeatureFirmaSaver {

    private const val TAG = "FeatureFirmaSaver"

    fun canHandle(table: String): Boolean {
        return table.equals("firma", ignoreCase = true) ||
                table.equals("company", ignoreCase = true) ||
                table.equals("companies", ignoreCase = true)
    }

    /**
     * @return başarılıysa kaydedilen/güncellenen id, başarısızsa -1L
     */
    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {
        return try {
            val firmaAdi = data["firmaAdi"]?.toString()?.trim().orEmpty()
            val adres = data["adres"]?.toString()?.trim().orEmpty()
            val telefon = PhoneUtils.toLocalTR(data["telefon"]?.toString())
            val vergiNo = data["vergiNo"]?.toString()?.trim().orEmpty()

            // 🆔 Senkronizasyon için hayati önem taşıyan UUID ve updatedAt
            val uuid = data["uuid"]?.toString().orEmpty()
            val updatedAt = (data["updatedAt"] as? Long) ?: System.currentTimeMillis()

            if (firmaAdi.isBlank()) {
                Toast.makeText(context, "⚠️ Firma adı boş olamaz", Toast.LENGTH_SHORT).show()
                return -1L
            }

            // 🛠️ Firma nesnesini oluştur
            val firma = Firma().apply {
                // Eğer editMode true ise mevcut ID'yi koru, değilse 0 bırak (Auto-increment)
                id = if (editMode) recordId else 0L
                this.firmaAdi = firmaAdi
                this.adres = adres
                this.telefon = telefon
                this.vergiNo = vergiNo

                // 🔐 Hibrit senkronizasyon alanlarını mühürle
                if (uuid.isNotEmpty()) {
                    this.uuid = uuid
                }
                this.updatedAt = updatedAt
            }

            val dao = MetaTakipFirmaDao(context)

            val ok = if (editMode && recordId > 0L) {
                Log.d(TAG, "🔄 Mevcut firma güncelleniyor: $firmaAdi (ID: $recordId)")
                dao.updateFirmaById(recordId, firma)
            } else {
                Log.d(TAG, "➕ Yeni firma ekleniyor: $firmaAdi")
                dao.addFirma(firma)
            }

            if (!ok) {
                Log.e(TAG, "❌ Veritabanı işlemi başarısız oldu: $firmaAdi")
                Toast.makeText(context, "❌ Firma kaydedilemedi", Toast.LENGTH_SHORT).show()
                return -1L
            }

            Log.d(TAG, "✅ Firma başarıyla mühürlendi: $firmaAdi")
            Toast.makeText(context, "✅ Firma kaydedildi", Toast.LENGTH_SHORT).show()

            // Başarılı işlem sonucunda güncel ID'yi dön
            if (editMode) recordId else 1L

        } catch (e: Exception) {
            Log.e(TAG, "save hata: ${e.message}", e)
            Toast.makeText(context, "❌ Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            -1L
        }
    }
}