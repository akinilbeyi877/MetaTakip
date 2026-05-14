package com.example.metatakip.feature_data.entityModel

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MetaTakipPrefs", Context.MODE_PRIVATE)

    // 🟢 PROPERTY'LER
    var currentUserId: Long
        get() = prefs.getLong("currentUserId", 0L)
        set(value) = prefs.edit().putLong("currentUserId", value).apply()

    var isAdmin: Boolean
        get() = prefs.getBoolean("isAdmin", false)
        set(value) = prefs.edit().putBoolean("isAdmin", value).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var userId: Long  // 🟢 LoginActivity için
        get() = prefs.getLong("userId", 0L)
        set(value) = prefs.edit().putLong("userId", value).apply()

    // 🟢 METODLAR
    fun isLoggedIn(): Boolean {
        return currentUserId != 0L
    }

    // 🟢 Login metodu
    fun login(userId: Long, username: String, isAdmin: Boolean = false) {
        this.currentUserId = userId
        this.userId = userId  // userId'yi de kaydet
        this.username = username
        this.isAdmin = isAdmin
    }

    // Alternatif login metodu
    fun login(userId: Long, username: String) {
        login(userId, username, false)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // 🟢 getUserId() FONKSİYONUNU KALDIRDIK - ÇÜNKÜ userId PROPERTY'Sİ VAR
    // ESKİ: fun getUserId(): Long = currentUserId // 🚫 BU SATIRI SİLİN

    fun getUserInfo(): String {
        return "ID: $currentUserId, Kullanıcı: ${username ?: "Bilinmiyor"}, Admin: $isAdmin"
    }
}