// File: FeatureFirmaLoader.kt
package com.example.metatakip.feature.firma.loaders

import android.content.Context
import com.example.metatakip.feature.firma.data.MetaTakipFirmaDaoImpl

object FeatureFirmaLoader {

    fun canHandle(table: String): Boolean {
        return table.equals("firma", ignoreCase = true) ||
                table.equals("company", ignoreCase = true) ||
                table.equals("companies", ignoreCase = true)
    }

    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val dao = MetaTakipFirmaDaoImpl(context)
            val firma = dao.getFirmaById(recordId) ?: return false

            fields["firmaAdi"] = firma.firmaAdi ?: ""
            fields["adres"] = firma.adres ?: ""
            fields["telefon"] = firma.telefon ?: ""
            fields["vergiNo"] = firma.vergiNo ?: ""

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}