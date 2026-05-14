package com.example.metatakip.helpers

import android.content.Context
import com.example.metatakip.controllers.callphonelast.PhoneUtils
import dao.MetaTakipCustomerDao

object FirmaResolver {

    data class FirmaResult(
        val firmaAdi: String?,
        val kullaniciAdi: String?
    )

    fun resolve(context: Context, rawPhone: String): FirmaResult {

        val normalized = PhoneUtils.normalize(rawPhone)

        val dao = MetaTakipCustomerDao(context)
        val customer = dao.findCustomerByNormalizedPhone(normalized)

        return if (customer != null) {
            FirmaResult(
                firmaAdi = customer.firmaAdi,
                kullaniciAdi = customer.adSoyad
            )
        } else {
            FirmaResult(null, null)
        }
    }
}
