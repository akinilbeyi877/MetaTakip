package com.example.metatakip.feature_data.common

object PhoneUtils {

    fun digitsOnly(input: String?): String =
        (input ?: "").replace("[^0-9]".toRegex(), "")

    /**
     * 🔑 Program içi "tek anahtar" normalize:
     * - Her şeyi rakama indir
     * - TR için son 10 haneyi al (5551112233)
     * - Boşsa "" döner
     */
    fun normalizeKeyTR(input: String?): String {
        val d = digitsOnly(input)
        if (d.isBlank()) return ""
        return if (d.length >= 10) d.takeLast(10) else d
    }

    /** 05xxxxxxxxx (TR lokal) döndürür */
    fun toLocalTR(input: String?): String {
        val d = digitsOnly(input)
        if (d.isBlank()) return ""

        return when {
            d.startsWith("0") && d.length == 11 -> d
            d.startsWith("90") && d.length == 12 -> "0" + d.substring(2)
            d.length == 10 && d.startsWith("5") -> "0$d"
            else -> {
                val last10 = if (d.length >= 10) d.takeLast(10) else d
                if (last10.length == 10) "0$last10" else d
            }
        }
    }

    /** 90xxxxxxxxxx (E.164, + yok) */
    fun toE164TR(input: String?): String {
        val d = digitsOnly(input)
        if (d.isBlank()) return ""

        return when {
            d.startsWith("90") && d.length == 12 -> d
            d.startsWith("0") && d.length == 11 -> "90" + d.substring(1)
            d.length == 10 && d.startsWith("5") -> "90$d"
            else -> {
                val last10 = if (d.length >= 10) d.takeLast(10) else d
                if (last10.length == 10) "90$last10" else d
            }
        }
    }
}