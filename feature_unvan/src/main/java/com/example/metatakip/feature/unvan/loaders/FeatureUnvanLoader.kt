// File: FeatureUnvanLoader.kt
package com.example.metatakip.feature.unvan.loaders

import android.content.Context
import android.util.Log
import com.example.metatakip.feature.unvan.data.UnvanDaoImpl

object FeatureUnvanLoader {

    private const val TAG = "FeatureUnvanLoader"

    fun canHandle(table: String): Boolean {
        return table.equals("unvan", ignoreCase = true) ||
                table.equals("title", ignoreCase = true) ||
                table.equals("titles", ignoreCase = true)
    }

    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val dao = UnvanDaoImpl(context)
            val unvan = dao.getUnvanById(recordId)

            if (unvan == null) {
                Log.w(TAG, "❌ Unvan bulunamadı: id=$recordId")
                return false
            }

            fields["ad"] = unvan.ad
            fields["aciklama"] = unvan.aciklama

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ load hata: ${e.message}", e)
            false
        }
    }
}