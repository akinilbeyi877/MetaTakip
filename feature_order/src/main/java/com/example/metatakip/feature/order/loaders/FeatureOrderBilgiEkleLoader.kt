package com.example.metatakip.feature.order.loaders

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb

/**
 * 🧩 FeatureOrderBilgiEkleLoader
 * GenericFormLoader içinde kullanılır.
 * Edit mode’da urun (siparis kalemi) kaydını DB’den çekip fields map’ine basar.
 *
 * Form key’leri FeatureOrderBilgiEkleTableFormProvider ile aynı olmalı:
 * urunAdi, adet, fiyat, en, boy, metrekare, ekNot
 *
 * DB’de: ad, adet, fiyat, m2 var.
 * en/boy/ekNot DB’de yoksa boş kalır.
 */
object FeatureOrderBilgiEkleLoader {

    private const val TAG = "FeatureOrderBilgiEkleLoader"

    fun canHandle(table: String): Boolean {
        return table.equals("siparis_bilgi_ekle", ignoreCase = true) ||
                table.equals("order_item_add", ignoreCase = true) ||
                table.equals("order_detail_add", ignoreCase = true)
    }

    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val db = MetaTakipDb.getInstance(context).readableDatabase

            db.rawQuery(
                "SELECT ad, adet, fiyat, m2 FROM urun WHERE id=? AND (isDeleted=0 OR isDeleted IS NULL) LIMIT 1",
                arrayOf(recordId.toString())
            ).use { c ->

                if (!c.moveToFirst()) {
                    Log.w(TAG, "❌ Ürün bulunamadı: id=$recordId")
                    return false
                }

                val ad = c.getString(c.getColumnIndexOrThrow("ad")) ?: ""
                val adet = c.getInt(c.getColumnIndexOrThrow("adet"))
                val fiyat = c.getDouble(c.getColumnIndexOrThrow("fiyat"))
                val m2 = c.getDouble(c.getColumnIndexOrThrow("m2"))

                fields["urunAdi"] = ad
                fields["adet"] = adet.toString()
                fields["fiyat"] = fiyat.toString()
                fields["metrekare"] = m2.toString()

                // en / boy / ekNot DB’de olmadığı için set etmiyoruz (form zaten "" gösterecek)
                Log.d(TAG, "✅ Ürün yüklendi: id=$recordId")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ürün load hatası: ${e.message}", e)
            false
        }
    }
}