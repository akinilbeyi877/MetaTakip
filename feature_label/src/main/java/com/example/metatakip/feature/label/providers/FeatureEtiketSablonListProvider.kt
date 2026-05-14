package com.example.metatakip.feature.label.providers

import android.content.Context
import com.example.metatakip.feature.label.data.EtiketSablonDaoImpl
import com.example.metatakip.feature_data.entityModel.SessionManager
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface
import java.util.Locale

class FeatureEtiketSablonListProvider(
    private val context: Context,
    private val dao: EtiketSablonDaoInterface = EtiketSablonDaoImpl(context)
) {

    fun canHandle(table: String): Boolean {
        return table.lowercase(Locale.ROOT) == "etiket_sablon"
    }

    fun load(): MutableList<Any> {
        val session = SessionManager(context)
        val userId = session.userId.toInt()

        // Firma adlarini reflection ile yukle (feature_firma modulune bagimlilik eklemeden)
        try {
            val klass = Class.forName("com.example.metatakip.feature.firma.data.MetaTakipFirmaDaoImpl")
            val instance = klass.getConstructor(Context::class.java).newInstance(context)
            @Suppress("UNCHECKED_CAST")
            val firmas = klass.getMethod("getAllFirmalar").invoke(instance) as List<Any>
            val byId = HashMap<Long, String>()
            val byUuid = HashMap<String, String>()
            for (f in firmas) {
                val id = (f.javaClass.getMethod("getId").invoke(f) as? Long) ?: 0L
                // Firma entity field: firmaAdi → getter getFirmaAdi (Kotlin auto-getter)
                val adi = try { f.javaClass.getMethod("getFirmaAdi").invoke(f) as? String ?: "" }
                          catch (_: Exception) {
                              try { f.javaClass.getMethod("getAdi").invoke(f) as? String ?: "" }
                              catch (_: Exception) { "" }
                          }
                val uuid = try { f.javaClass.getMethod("getUuid").invoke(f) as? String ?: "" }
                           catch (_: Exception) { "" }
                if (id > 0L && adi.isNotBlank()) byId[id] = adi
                if (uuid.isNotBlank() && adi.isNotBlank()) byUuid[uuid] = adi
            }
            com.example.metatakip.feature_data.ui.mapper.GenericListUiMapper.firmaNameById = byId
            com.example.metatakip.feature_data.ui.mapper.GenericListUiMapper.firmaNameByUuid = byUuid
            android.util.Log.i("EtiketSablonProvider", "Firma lookup yuklendi: byId=" + byId.size + " byUuid=" + byUuid.size)
            byId.forEach { (k, v) -> android.util.Log.d("EtiketSablonProvider", "   firma id=$k → '$v'") }
        } catch (e: Exception) {
            android.util.Log.w("EtiketSablonProvider", "Firma lookup yuklenemedi: " + e.message)
        }

        return dao.getAllSablonlar(userId)
            .map { it as Any }
            .toMutableList()
    }
}