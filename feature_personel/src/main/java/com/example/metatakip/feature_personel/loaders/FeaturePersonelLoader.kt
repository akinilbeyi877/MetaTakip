package com.example.metatakip.feature_personel.loaders

import android.content.Context
import com.example.metatakip.feature_personel.data.MetaTakipPersonelDaoImpl

object FeaturePersonelLoader {

    fun canHandle(table: String): Boolean =
        table.equals("personel", ignoreCase = true)

    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val dao = MetaTakipPersonelDaoImpl(context)
            val personel = dao.getPersonelById(recordId) ?: return false

            fields["adSoyad"] = personel.adSoyad
            fields["unvan"] = personel.unvan

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}