package com.example.metatakip.feature.customer.loaders

import android.content.Context
import android.util.Log
import dao.MetaTakipCustomerDao

/**
 * 🧩 FeatureCustomerLoader
 * GenericFormLoader içinde kullanılır.
 * Edit mode’da musteri/customer kaydını DB’den çekip fields map’ine basar.
 *
 * 🔑 Key’ler FeatureCustomerTableFormProvider ile aynı olmalı.
 */
object FeatureCustomerLoader {

    private const val TAG = "FeatureCustomerLoader"

    fun canHandle(table: String): Boolean {
        return table.equals("musteri", ignoreCase = true) ||
                table.equals("customer", ignoreCase = true) ||
                table.equals("customers", ignoreCase = true)
    }

    /**
     * @param fields: GenericFormLoader’ın doldurduğu map
     */
    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val dao = MetaTakipCustomerDao(context)
            val customer = dao.getCustomerById(recordId)

            if (customer == null) {
                Log.w(TAG, "❌ Customer bulunamadı: id=$recordId")
                return false
            }

            // ✅ Provider ile aynı key’ler
            fields["firmaid"] = (customer.firmaid ?: 0L).toString()

            // Bazı projelerde ayrıca "firmaAdi" alanı gösteriliyor (opsiyonel)
            // Provider’ında varsa dolacak şekilde bıraktım:
            fields["firmaAdi"] = customer.firmaAdi ?: ""

            fields["adSoyad"] = customer.adSoyad
            fields["ceptel"] = customer.ceptel ?: ""
            fields["ceptel2"] = customer.ceptel2 ?: ""

            fields["bolge"] = customer.bolge ?: ""
            fields["adres"] = customer.adres ?: ""
            fields["musteriNotu"] = customer.musteriNotu ?: ""

            Log.d(TAG, "✅ Customer yüklendi: id=$recordId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Customer load hatası: ${e.message}", e)
            false
        }
    }
}