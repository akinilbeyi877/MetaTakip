package com.example.metatakip.feature.order.loaders

import android.content.Context
import android.util.Log
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.common.PhoneUtils

/**
 * 🧩 FeatureOrderLoader
 * GenericFormLoader içinde kullanılır.
 *
 * Edit mode’da siparis/order kaydını DB’den çekip fields map’ine basar.
 *
 * Not:
 * - Sipariş tablosunda artık kopya müşteri/firma verisi tutulmaz.
 * - OrderDaoImpl JOIN ile musteri/firma bilgisini getirir.
 * - Bu yüzden burada order.musteriAdi / musteriTelefon / firmaAdi
 *   alanları gösterim amaçlı güvenle kullanılabilir.
 */
object FeatureOrderLoader {

    private const val TAG = "FeatureOrderLoader"

    fun canHandle(table: String): Boolean {
        return table.equals("siparis", ignoreCase = true) ||
                table.equals("order", ignoreCase = true) ||
                table.equals("orders", ignoreCase = true)
    }

    /**
     * @param fields GenericFormLoader’ın doldurduğu map
     * key’ler FeatureOrderTableFormProvider ile aynı olmalı.
     */
    fun load(
        context: Context,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            if (recordId <= 0L) {
                Log.w(TAG, "❌ Geçersiz order id: $recordId")
                return false
            }

            val dao = OrderDaoImpl(context)
            val order = dao.getOrderById(recordId)

            if (order == null) {
                Log.w(TAG, "❌ Order bulunamadı: id=$recordId")
                return false
            }

            // ✅ Gösterim alanları (JOIN ile geliyor)
            fields["musteriAdi"] = order.musteriAdi.orEmpty()
            fields["musteriTelefon"] = PhoneUtils.toLocalTR(order.musteriTelefon)
            fields["firmaAdi"] = order.firmaAdi.orEmpty()

            // ✅ İlişki alanları
            fields["firmaid"] = order.firmaId.toString()

            // ✅ Sipariş alanları
            fields["urunTipi"] = order.urunTipi.orEmpty()
            fields["yetkili"] = order.yetkili.orEmpty()
            fields["durum"] = order.durum.orEmpty()
            fields["teslimAlmaTarihi"] = order.teslimAlmaTarihi.orEmpty()
            fields["teslimTarihi"] = order.teslimTarihi.orEmpty()
            fields["notlar"] = order.notlar.orEmpty()

            Log.d(TAG, "✅ Order yüklendi: id=$recordId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Order load hatası: ${e.message}", e)
            false
        }
    }
}