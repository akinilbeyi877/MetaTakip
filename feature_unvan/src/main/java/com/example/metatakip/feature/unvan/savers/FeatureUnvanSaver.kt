// File: FeatureUnvanSaver.kt
package com.example.metatakip.feature.unvan.savers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.metatakip.feature.unvan.data.UnvanDaoImpl
import com.example.metatakip.feature_data.entityModel.Unvan

object FeatureUnvanSaver {

    private const val TAG = "FeatureUnvanSaver"

    fun canHandle(table: String): Boolean {
        return table.equals("unvan", ignoreCase = true) ||
                table.equals("title", ignoreCase = true) ||
                table.equals("titles", ignoreCase = true)
    }

    /**
     * @return id (editMode'da recordId), başarısızsa -1L
     */
    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {
        return try {
            val ad = data["ad"]?.toString()?.trim().orEmpty()
            val aciklama = data["aciklama"]?.toString()?.trim().orEmpty()

            if (ad.isBlank()) {
                Toast.makeText(context, "⚠️ Ünvan adı boş olamaz", Toast.LENGTH_SHORT).show()
                return -1L
            }

            val unvan = Unvan(
                id = if (editMode) recordId else 0L,
                ad = ad,
                aciklama = aciklama
            )

            val dao = UnvanDaoImpl(context)

            val id = if (editMode) {
                val ok = dao.updateUnvanById(recordId, unvan)
                if (ok) recordId else -1L
            } else {
                dao.addUnvan(unvan)
            }

            if (id <= 0L) {
                Toast.makeText(context, "❌ Ünvan kaydedilemedi", Toast.LENGTH_SHORT).show()
                return -1L
            }

            Toast.makeText(context, "✅ Ünvan kaydedildi", Toast.LENGTH_SHORT).show()
            id

        } catch (e: Exception) {
            Log.e(TAG, "save hata: ${e.message}", e)
            Toast.makeText(context, "❌ Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            -1L
        }
    }
}