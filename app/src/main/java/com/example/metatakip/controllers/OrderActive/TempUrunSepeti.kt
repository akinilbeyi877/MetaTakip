package com.example.metatakip.controllers.OrderActive

import android.util.Log

data class TempUrunItem(
    val tempId: Long,
    val siparisId: Long,
    val ad: String,
    val adet: Int,
    val m2: Double,
    val fiyat: Double,
    val urunIndirim: Double = 0.0,
    val urunIndirimAciklamasi: String = "",
    val urunEkUcret: Double = 0.0,
    val urunEkUcretAciklamasi: String = ""
) {
    val hamTutar: Double get() = if (m2 > 0.0) m2 * fiyat else adet * fiyat
    val indirimliTutar: Double get() = hamTutar - urunIndirim
    val toplamTutar: Double get() = indirimliTutar + urunEkUcret
}

object TempUrunSepeti {
    private const val TAG = "TempUrunSepeti"
    private var nextId = 1L
    private val map = mutableMapOf<Long, MutableList<TempUrunItem>>()

    fun getItems(siparisId: Long): List<TempUrunItem> {
        val items = map[siparisId]?.toList() ?: emptyList()
        Log.d(TAG, "📦 getItems - Sipariş ID: $siparisId, Ürün sayısı: ${items.size}")
        items.forEach { item ->
            Log.d(TAG, "   - Ürün: ${item.ad}, İndirim: ${item.urunIndirim}, EkÜcret: ${item.urunEkUcret}")
        }
        return items
    }

    fun addItem(
        siparisId: Long,
        ad: String,
        adet: Int,
        m2: Double,
        fiyat: Double,
        urunIndirim: Double = 0.0,
        urunIndirimAciklamasi: String = "",
        urunEkUcret: Double = 0.0,
        urunEkUcretAciklamasi: String = ""
    ): TempUrunItem {
        Log.d(TAG, "🔥🔥🔥 addItem ÇAĞRILDI! 🔥🔥🔥")
        Log.d(TAG, "   Sipariş ID: $siparisId")
        Log.d(TAG, "   Ürün: $ad")
        Log.d(TAG, "   Adet: $adet")
        Log.d(TAG, "   M2: $m2")
        Log.d(TAG, "   Fiyat: $fiyat")
        Log.d(TAG, "   💰 İndirim: $urunIndirim")
        Log.d(TAG, "   📝 İndirim Açıklaması: $urunIndirimAciklamasi")
        Log.d(TAG, "   💵 Ek Ücret: $urunEkUcret")
        Log.d(TAG, "   📝 Ek Ücret Açıklaması: $urunEkUcretAciklamasi")

        val item = TempUrunItem(
            nextId++, siparisId, ad, adet, m2, fiyat,
            urunIndirim, urunIndirimAciklamasi,
            urunEkUcret, urunEkUcretAciklamasi
        )

        val list = map.getOrPut(siparisId) { mutableListOf() }
        list.add(item)

        Log.d(TAG, "   ✅ Ürün eklendi! Toplam ürün sayısı: ${list.size}")
        Log.d(TAG, "   🆔 Temp ID: ${item.tempId}")

        return item
    }

    fun removeItem(siparisId: Long, tempId: Long): Boolean {
        Log.d(TAG, "🗑️ removeItem - Sipariş ID: $siparisId, Temp ID: $tempId")
        val result = map[siparisId]?.removeAll { it.tempId == tempId } ?: false
        if (result) {
            Log.d(TAG, "   ✅ Ürün silindi")
        } else {
            Log.d(TAG, "   ❌ Ürün bulunamadı")
        }
        return result
    }

    fun clear(siparisId: Long) {
        Log.d(TAG, "🧹 clear - Sipariş ID: $siparisId, Silinen ürün sayısı: ${map[siparisId]?.size ?: 0}")
        map.remove(siparisId)
    }

    // 🔥 Tüm verileri göster (debug için)
    fun dumpAll() {
        Log.d(TAG, "========== TÜM GEÇİCİ ÜRÜNLER ==========")
        if (map.isEmpty()) {
            Log.d(TAG, "Sepet BOŞ!")
        } else {
            map.forEach { (siparisId, items) ->
                Log.d(TAG, "Sipariş ID: $siparisId - ${items.size} ürün")
                items.forEachIndexed { index, item ->
                    Log.d(TAG, "  [$index] ${item.ad} | İndirim:${item.urunIndirim} | EkÜcret:${item.urunEkUcret}")
                }
            }
        }
        Log.d(TAG, "========================================")
    }
}