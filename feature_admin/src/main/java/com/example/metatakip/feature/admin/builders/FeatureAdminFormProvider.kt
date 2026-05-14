package com.example.metatakip.feature.admin.builders

import android.content.Context
import com.example.metatakip.feature.admin.data.MesajSablonDaoImpl
import com.example.metatakip.feature_data.entityModel.MesajSablon

class FeatureAdminFormProvider(
    private val context: Context
) {

    fun canHandle(table: String): Boolean {
        return table in listOf(
            "mesaj_sablon",
            "admin_firma"
        )
    }

    /**
     * 🟢 DÜZELTME: Boolean yerine Long döndürüyoruz.
     * Bu sayede GenericFormSaver gerçek ID'yi yakalayıp "Başarılı" diyebilecek.
     */
    fun save(
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {
        return when (table) {
            "mesaj_sablon" -> saveMesajSablon(data, editMode, recordId)
            else -> -1L
        }
    }

    // =======================
    // LOAD
    // =======================
    fun load(
        table: String,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return when (table) {
            "mesaj_sablon" -> loadMesajSablon(recordId, fields)
            else -> false
        }
    }

    /**
     * 🟢 MESAJ SABLON SAVE:
     * Veritabanından gelen yeni ID'yi (Örn: 16) geri döndürür.
     */
    private fun saveMesajSablon(
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {

        // 1. FIRMA BİLGİLERİ (ZORUNLU)
        val firmaId = parseFirmaId(data["firmaid"])
        if (firmaId <= 0) return -1L

        val firmaAdi = data["firma_adi"]?.toString()?.trim() ?: ""

        // 2. DİĞER ZORUNLU ALANLAR
        val baslik = data["baslik"]?.toString()?.trim() ?: return -1L
        val musteriOlustuMesaj = data["musteri_olustu_mesaj"]?.toString()?.trim() ?: return -1L
        val musteriGuncellendiMesaj = data["musteri_guncellendi_mesaj"]?.toString()?.trim() ?: return -1L
        val siparisOlustuMesaj = data["siparis_olustu_mesaj"]?.toString()?.trim() ?: return -1L
        val siparisUrunEklendiMesaj = data["siparis_urun_eklendi_mesaj"]?.toString()?.trim() ?: return -1L
        val smsOnayMesaj = data["sms_onay_mesaj"]?.toString()?.trim() ?: return -1L
        val whatsappOnayMesaj = data["whatsapp_onay_mesaj"]?.toString()?.trim() ?: return -1L

        // 3. VARSAYILAN ALANI
        val varsayilan = parseBoolean(data["varsayilan"])

        val dao = MesajSablonDaoImpl(context)

        val sablon = MesajSablon(
            id = recordId,
            firmaid = firmaId,
            firmaAdi = firmaAdi,
            baslik = baslik,
            musteriOlustuMesaj = musteriOlustuMesaj,
            musteriGuncellendiMesaj = musteriGuncellendiMesaj,
            siparisOlustuMesaj = siparisOlustuMesaj,
            siparisUrunEklendiMesaj = siparisUrunEklendiMesaj,
            smsOnayMesaj = smsOnayMesaj,
            whatsappOnayMesaj = whatsappOnayMesaj,
            varsayilan = varsayilan
        )

        return if (editMode && recordId != -1L) {
            // GÜNCELLEME İŞLEMİ
            val success = dao.update(recordId, sablon)
            if (success && varsayilan) {
                dao.setVarsayilanForFirma(firmaId, recordId)
            }
            if (success) recordId else -1L
        } else {
            // YENİ KAYIT İŞLEMİ
            val newId = dao.insert(sablon)
            if (newId > 0 && varsayilan) {
                dao.setVarsayilanForFirma(firmaId, newId)
            }
            newId // Burası artık logdaki 'ID: 16'yı döndürecek
        }
    }

    private fun loadMesajSablon(
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        val dao = MesajSablonDaoImpl(context)
        val sablon = dao.getById(recordId) ?: return false

        fields["firmaid"] = sablon.firmaid
        fields["firma_adi"] = sablon.firmaAdi
        fields["baslik"] = sablon.baslik
        fields["musteri_olustu_mesaj"] = sablon.musteriOlustuMesaj
        fields["musteri_guncellendi_mesaj"] = sablon.musteriGuncellendiMesaj
        fields["siparis_olustu_mesaj"] = sablon.siparisOlustuMesaj
        fields["siparis_urun_eklendi_mesaj"] = sablon.siparisUrunEklendiMesaj
        fields["sms_onay_mesaj"] = sablon.smsOnayMesaj
        fields["whatsapp_onay_mesaj"] = sablon.whatsappOnayMesaj
        fields["varsayilan"] = sablon.varsayilan

        return true
    }

    private fun parseFirmaId(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun parseBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }
}