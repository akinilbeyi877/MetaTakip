package com.example.metatakip.feature.uruntipi.builders

import android.content.Context
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoInterface

/**
 * 📋 UrunTipiListBuilder
 * Admin sistemi için liste sağlayıcı
 * ✅ KOD ALANI YOK!
 */
class UrunTipiListBuilder(private val context: Context) {

    fun canHandle(tableName: String): Boolean {
        return tableName.equals("urun_tipi", ignoreCase = true) ||
                tableName.equals("urun tipi", ignoreCase = true) ||
                tableName.equals("product_type", ignoreCase = true)
    }

    /**
     * 📊 Liste verisini getir (detaylı)
     * ✅ KOD ALANI YOK!
     */
    fun loadListData(dao: UrunTipiDaoInterface): List<Map<String, Any>> {
        return try {
            val urunTipleri = dao.getAll()

            urunTipleri.map { urunTipi ->
                mapOf(
                    "id" to urunTipi.id,
                    // ✅ KOD YOK!
                    "ad" to urunTipi.ad,
                    "birimFiyat" to urunTipi.birimFiyat,
                    "formattedFiyat" to String.format("%.2f ₺", urunTipi.birimFiyat),
                    "hesapTipi" to urunTipi.hesapTipi,
                    "aktif" to urunTipi.aktif,
                    "aktifText" to if (urunTipi.aktif == 1) "✅ Aktif" else "❌ Pasif",
                    "aktifColor" to if (urunTipi.aktif == 1) "#4CAF50" else "#F44336",
                    "display" to urunTipi.ad, // ✅ KOD YOK, sadece AD
                    "shortDisplay" to urunTipi.ad,
                    "aciklama" to (urunTipi.aciklama ?: "") // ✅ Açıklama eklendi
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 🎯 Kolon başlıkları (detaylı)
     * ✅ KOD KOLONU YOK!
     */
    fun getColumnHeaders(): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "key" to "ad", // ✅ KOD YERİNE AD
                "label" to "Ürün Tipi",
                "width" to 0.35f, // ✅ Daha geniş (KOD olmadığı için)
                "sortable" to true
            ),
            mapOf(
                "key" to "formattedFiyat",
                "label" to "Birim Fiyat",
                "width" to 0.20f,
                "sortable" to true,
                "align" to "end"
            ),
            mapOf(
                "key" to "hesapTipi",
                "label" to "Hesap Tipi",
                "width" to 0.20f,
                "sortable" to true
            ),
            mapOf(
                "key" to "aktifText",
                "label" to "Durum",
                "width" to 0.15f,
                "sortable" to true
            ),
            mapOf(
                "key" to "actions",
                "label" to "İşlemler",
                "width" to 0.10f,
                "sortable" to false
            )
        )
    }

    /**
     * 🔍 Filtreleme (gelişmiş)
     * ✅ KOD ARAMA YOK!
     */
    fun filterList(
        dao: UrunTipiDaoInterface,
        query: String,
        onlyActive: Boolean = false
    ): List<Map<String, Any>> {
        return try {
            val allData = if (onlyActive) {
                dao.getActive().map { urunTipi ->
                    mapOf(
                        "id" to urunTipi.id,
                        "ad" to urunTipi.ad, // ✅ KOD YOK!
                        "birimFiyat" to urunTipi.birimFiyat,
                        "formattedFiyat" to String.format("%.2f ₺", urunTipi.birimFiyat),
                        "hesapTipi" to urunTipi.hesapTipi,
                        "aktif" to urunTipi.aktif,
                        "aciklama" to (urunTipi.aciklama ?: "")
                    )
                }
            } else {
                loadListData(dao)
            }

            if (query.isEmpty()) {
                return allData
            }

            allData.filter { item ->
                // ✅ SADECE AD ve AÇIKLAMA'da arama
                item["ad"]?.toString()?.contains(query, ignoreCase = true) == true ||
                        item["hesapTipi"]?.toString()?.contains(query, ignoreCase = true) == true ||
                        item["aciklama"]?.toString()?.contains(query, ignoreCase = true) == true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 📈 İstatistikler
     */
    fun getStatistics(dao: UrunTipiDaoInterface): Map<String, Any> {
        return try {
            val all = dao.getAll()
            val active = dao.getActive()

            mapOf(
                "total" to all.size,
                "active" to active.size,
                "inactive" to all.size - active.size,
                "avgPrice" to all.map { it.birimFiyat }.average(),
                "types" to all.groupBy { it.hesapTipi }.mapValues { it.value.size }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * 📤 Dışa aktarım için veri
     * ✅ KOD ALANI YOK!
     */
    fun getExportData(dao: UrunTipiDaoInterface): List<Map<String, String>> {
        return loadListData(dao).map { item ->
            mapOf(
                // ✅ KOD YOK!
                "Ürün Tipi" to (item["ad"]?.toString() ?: ""),
                "Birim Fiyat (₺)" to (item["formattedFiyat"]?.toString() ?: ""),
                "Hesap Tipi" to (item["hesapTipi"]?.toString() ?: ""),
                "Durum" to (item["aktifText"]?.toString() ?: ""),
                "Açıklama" to (item["aciklama"]?.toString() ?: ""),
                "ID" to (item["id"]?.toString() ?: "")
            )
        }
    }
}