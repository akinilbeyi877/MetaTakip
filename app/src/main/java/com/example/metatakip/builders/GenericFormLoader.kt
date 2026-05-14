// File: GenericFormLoader.kt
package com.example.metatakip.builders

import android.content.Context
import com.example.metatakip.feature.admin.builders.FeatureAdminFormProvider
import com.example.metatakip.feature.customer.loaders.FeatureCustomerLoader
import com.example.metatakip.feature.firma.loaders.FeatureFirmaLoader
import com.example.metatakip.feature.label.loaders.FeatureEtiketSablonLoader
import com.example.metatakip.feature.order.loaders.FeatureOrderBilgiEkleLoader
import com.example.metatakip.feature.order.loaders.FeatureOrderLoader
import com.example.metatakip.feature.unvan.loaders.FeatureUnvanLoader
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_personel.loaders.FeaturePersonelLoader

class GenericFormLoader(
    private val context: Context
) {

    fun load(
        table: String,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {

        // ✅ CUSTOMER
        if (FeatureCustomerLoader.canHandle(table)) {
            return FeatureCustomerLoader.load(context, recordId, fields)
        }

        // ✅ FIRMA
        if (FeatureFirmaLoader.canHandle(table)) {
            return FeatureFirmaLoader.load(context, recordId, fields)
        }

        // ✅ ORDER
        if (FeatureOrderLoader.canHandle(table)) {
            return FeatureOrderLoader.load(context, recordId, fields)
        }

        // ✅ ORDER - BİLGİ EKLE
        if (FeatureOrderBilgiEkleLoader.canHandle(table)) {
            return FeatureOrderBilgiEkleLoader.load(context, recordId, fields)
        }

        // ✅ UNVAN
        if (FeatureUnvanLoader.canHandle(table)) {
            return FeatureUnvanLoader.load(context, recordId, fields)
        }

        // ✅ PERSONEL
        if (FeaturePersonelLoader.canHandle(table)) {
            return FeaturePersonelLoader.load(context, recordId, fields)
        }

        // ✅ ETİKET ŞABLON (class -> instance)
        val etiketLoader = FeatureEtiketSablonLoader(context)
        if (etiketLoader.canHandle(table)) {
            // ⚠️ Burada FeatureEtiketSablonLoader içinde map overload olmalı:
            // fun load(recordId: Long, fields: MutableMap<String, Any?>): Boolean
            return etiketLoader.load(recordId, fields)
        }

        // ✅ ÜRÜN TİPİ (local)
        if (table.lowercase() in listOf("urun_tipi", "urun tipi", "product_type")) {
            return loadUrunTipiData(recordId, fields)
        }

        // ✅ ADMIN
        val adminProvider = FeatureAdminFormProvider(context)
        if (adminProvider.canHandle(table)) {
            return adminProvider.load(table, recordId, fields)
        }

        return false
    }

    private fun loadUrunTipiData(
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val urunTipiDao = UrunTipiDaoImpl(context)
            val urunTipleri = urunTipiDao.getAll()
            val urunTipi = urunTipleri.find { it.id == recordId }

            if (urunTipi != null) {
                fields["ad"] = urunTipi.ad
                fields["birimFiyat"] = urunTipi.birimFiyat.toString()
                fields["hesapTipi"] = urunTipi.hesapTipi
                fields["aktif"] = if (urunTipi.aktif == 1) "1" else "0"
                fields["aciklama"] = urunTipi.aciklama ?: ""
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}