package com.example.metatakip.controllers.callphonelast


object PhoneUtils {

    fun normalize(phone: String): String {
        if (phone.isBlank()) return ""

        var p = phone.replace("[^0-9]".toRegex(), "")

        when {
            p.startsWith("90") && p.length > 10 -> p = p.takeLast(10)
            p.startsWith("0") && p.length > 10 -> p = p.takeLast(10)
            p.length > 10 -> p.takeLast(10)
        }

        return p
    }
}
