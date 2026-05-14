package com.example.metatakip.utils.PhoneUtils



object PhoneUtils {

    fun normalize(phone: String): String {
        return phone
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")
            .trim()
    }
}
