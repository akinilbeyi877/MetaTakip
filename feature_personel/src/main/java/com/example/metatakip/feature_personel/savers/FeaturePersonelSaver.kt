package com.example.metatakip.feature_personel.savers

import android.content.Context
import android.widget.Toast
import com.example.metatakip.feature_data.entityModel.Personel
import com.example.metatakip.feature_personel.data.MetaTakipPersonelDaoImpl

object FeaturePersonelSaver {

    fun canHandle(table: String): Boolean =
        table.equals("personel", ignoreCase = true)

    /**
     * GenericFormSaver uyumlu entrypoint:
     * @return kayıt id (>0) ise başarı
     */
    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {
        return try {
            val adSoyad = data["adSoyad"]?.toString()?.trim().orEmpty()
            val unvanAdi = data["unvan"]?.toString()?.trim().orEmpty()

            if (adSoyad.isBlank()) {
                Toast.makeText(context, "⚠️ Personel adı boş olamaz", Toast.LENGTH_SHORT).show()
                return -1L
            }
            if (unvanAdi.isBlank() || unvanAdi == "Ünvan bulunamadı") {
                Toast.makeText(context, "⚠️ Ünvan seçilmelidir", Toast.LENGTH_SHORT).show()
                return -1L
            }

            val dao = MetaTakipPersonelDaoImpl(context)

            val personel = Personel(
                id = if (editMode) recordId else 0L,
                adSoyad = adSoyad,
                unvan = unvanAdi
            )

            val success = if (editMode) {
                dao.updatePersonelById(recordId, personel)
            } else {
                dao.addPersonel(personel)
            }

            Toast.makeText(
                context,
                if (success) "✅ Personel kaydedildi" else "❌ Personel kaydedilemedi",
                Toast.LENGTH_SHORT
            ).show()

            if (success) {
                // insert sonrası id dönmüyoruz (DAO boolean). editMode ise recordId’yi döndürmek mantıklı.
                if (editMode) recordId else 1L
            } else {
                -1L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Personel kaydedilemedi", Toast.LENGTH_SHORT).show()
            -1L
        }
    }
}