package com.example.metatakip.feature.label.loaders

import android.content.Context
import com.example.metatakip.feature.label.data.EtiketSablonDaoImpl
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface

class FeatureEtiketSablonLoader(
    private val context: Context,
    private val dao: EtiketSablonDaoInterface = EtiketSablonDaoImpl(context)
) {

    fun canHandle(table: String): Boolean {
        return table.equals("etiket_sablon", ignoreCase = true)
    }

    fun load(recordId: Long, fields: MutableList<FormField>): Boolean {
        val sablon = dao.getSablonById(recordId) ?: return false
        fields.forEach { field ->
            when (field.key) {
                "adi"        -> field.value = sablon.adi
                "firma_id"   -> field.value = sablon.firmaId.toString()
                "firma_uuid" -> field.value = sablon.firmaUuid
            }
            field.isEditMode = true
        }
        return true
    }

    fun load(recordId: Long, fields: MutableMap<String, Any?>): Boolean {
        val sablon = dao.getSablonById(recordId) ?: return false
        fields["adi"]        = sablon.adi
        fields["firma_id"]   = sablon.firmaId
        fields["firma_uuid"] = sablon.firmaUuid
        return true
    }
}
