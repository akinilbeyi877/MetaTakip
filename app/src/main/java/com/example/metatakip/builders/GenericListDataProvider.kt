package com.example.metatakip.builders

import android.content.Context
import com.example.metatakip.feature.admin.builders.FeatureAdminListProvider
import com.example.metatakip.feature.customer.providers.FeatureCustomerListProvider
import com.example.metatakip.feature.firma.providers.FeatureFirmaListProvider
import com.example.metatakip.feature.label.providers.FeatureEtiketSablonListProvider
import com.example.metatakip.feature.order.providers.FeatureOrderListProvider
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_personel.providers.FeaturePersonelListProvider
import java.util.Locale

class GenericListDataProvider(
    private val context: Context
) {

    /**
     * ✅ GenericListDataProvider sadece ROUTER.
     * - Listeyi kim handle ediyorsa ona yönlendirir.
     * - Core DAO'lara doğrudan gitmez (UrunTipi hariç; istersen provider yaparız)
     */
    fun load(table: String, durumFilter: String? = null): MutableList<Any> {
        val t = normalize(table)

        // 0️⃣ Order feature (✅ normal + silinen + durumFilter)
        FeatureOrderListProvider(context).let { provider ->
            if (provider.canHandle(t)) {
                val includeDeleted = isDeletedOrderListType(t)
                return provider.load(
                    durumFilter = durumFilter ?: "TÜMÜ",
                    includeDeleted = includeDeleted
                )
            }
        }

        // 1️⃣ Firma feature
        FeatureFirmaListProvider(context).let { provider ->
            if (provider.canHandle(t)) return provider.load()
        }

        // 2️⃣ Customer feature (✅ silinen destekli)
        FeatureCustomerListProvider(context).let { provider ->
            if (provider.canHandle(t)) {
                val includeDeleted = isDeletedCustomerListType(t)
                return provider.load(includeDeleted = includeDeleted)
            }
        }

        // 3️⃣ Personel feature
        FeaturePersonelListProvider(context).let { provider ->
            if (provider.canHandle(t)) return provider.load()
        }

        // 4️⃣ Etiket Şablon feature
        FeatureEtiketSablonListProvider(context).let { provider ->
            if (provider.canHandle(t)) return provider.load()
        }

        // 5️⃣ Admin feature (mesaj_sablon vb.)
        FeatureAdminListProvider(context).let { provider ->
            if (provider.canHandle(t)) return provider.load(t)
        }

        // 6️⃣ Ürün Tipi (şimdilik direkt dao)
        if (t == "urun_tipi" || t == "urun_tipi_" || t == "urun tipi" || t == "product_type") {
            return UrunTipiDaoImpl(context)
                .getAll()
                .map { it as Any }
                .toMutableList()
        }

        // 7️⃣ Çağrı Kayıtları
        if (t == "call_log" || t == "cagri_kaydi" || t == "cagri") {
            return com.example.metatakip.data.metaTakipDb.crud.CallLogsDao(context)
                .getAllCallLogs()
                .map { it as Any }
                .toMutableList()
        }

        // 8️⃣ Handle eden yoksa boş liste
        return mutableListOf()
    }

    // =============================================================
    // HELPERS
    // =============================================================
    private fun normalize(input: String): String {
        return input
            .trim()
            .lowercase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_")
    }

    private fun isDeletedCustomerListType(t: String): Boolean {
        return t == "musteri_silinen" ||
                t == "musteriler_silinen" ||
                t == "deleted_customers" ||
                t == "customers_deleted"
    }

    private fun isDeletedOrderListType(t: String): Boolean {
        return t == "siparis_silinen" ||
                t == "siparisler_silinen" ||
                t == "deleted_orders" ||
                t == "orders_deleted"
    }
}