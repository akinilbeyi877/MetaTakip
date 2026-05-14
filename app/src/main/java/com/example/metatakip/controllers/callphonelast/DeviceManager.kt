package com.example.metatakip.controllers.callphonelast

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.entityModel.DeviceConfig
import com.google.gson.Gson

/**
 * 📱 DeviceManager
 *
 * Cihaz yapılandırması için TEK ve MERKEZ sınıf
 * Kritik ayarlar (IP, rol vb.) cache'lenmez
 */
object DeviceManager {

    private const val TAG = "DeviceManager"

    private const val PREFS_NAME = "device_config"
    private const val KEY_CONFIG = "device_config"
    private const val KEY_CONFIGURED = "is_configured"

    private val gson = Gson()

    /**
     * 🔄 (Opsiyonel) Uygulama açılışında çağrılabilir
     * Artık cache yok → sadece log amaçlı
     */
    fun initialize(context: Context) {
        Log.d(TAG, "initialize() çağrıldı")
    }

    /**
     * ✅ Cihaz yapılandırılmış mı?
     */
    fun isDeviceConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONFIGURED, false)
    }

    /**
     * 💾 Yapılandırmayı kaydet
     * (IP değiştiğinde MUTLAKA burası çağrılmalı)
     */
    fun saveConfig(context: Context, config: DeviceConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putString(KEY_CONFIG, gson.toJson(config))
            .putBoolean(KEY_CONFIGURED, true)
            .apply()

        Log.d(TAG, "💾 Config kaydedildi → ${config.centralIp}")
    }

    /**
     * 📥 HER ZAMAN SharedPreferences'tan OKUR
     */
    fun getDeviceConfig(context: Context): DeviceConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null)

        if (json.isNullOrBlank()) return null

        return try {
            gson.fromJson(json, DeviceConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Config parse hatası", e)
            null
        }
    }

    /**
     * 🏢 Merkez cihaz mı?
     */
    fun isCentralDevice(context: Context): Boolean {
        return getDeviceConfig(context)?.isCentralDevice == true
    }

    /**
     * 🏢 Firma adı
     */
    fun getCompanyName(context: Context): String {
        return getDeviceConfig(context)?.companyName ?: "Bilinmiyor"
    }

    /**
     * 👤 Kullanıcı adı
     */
    fun getUserName(context: Context): String {
        return getDeviceConfig(context)?.userName ?: "Bilinmiyor"
    }

    /**
     * 🎯 Rol (SAHA / MERKEZ)
     */
    fun getUserRole(context: Context): String {
        return getDeviceConfig(context)?.userRole ?: "SAHA"
    }

    /**
     * 🌐 MERKEZ IP ADRESİ
     */
    fun getCentralIp(context: Context): String {
        return getDeviceConfig(context)?.centralIp ?: ""
    }

    /**
     * 🔍 Merkez IP tanımlı mı?
     */
    fun hasCentralIp(context: Context): Boolean {
        return getCentralIp(context).isNotBlank()
    }
}
