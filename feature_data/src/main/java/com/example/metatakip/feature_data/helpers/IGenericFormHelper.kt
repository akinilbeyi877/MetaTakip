package com.example.metatakip.feature_data.helpers

import android.app.Activity
import android.content.Intent

interface IGenericFormHelper {

    /** Voice intent üretir (Activity voiceLauncher.launch(...) ile kullanır) */
    fun buildVoiceIntent(languageTag: String = "tr-TR", prompt: String = "Konuşabilirsiniz..."): Intent

    /** RecognizerIntent result'ından metni okur */
    fun extractVoiceText(data: Intent?): String

    /** Eksik olan izinleri döner (sadece gerekli olanlar) */
    fun getMissingPhonePermissions(activity: Activity): Array<String>

    /** Tekli toast (öncekini kapatıp yenisini gösterir) */
    fun showSingleToast(activity: Activity, message: String, long: Boolean = true)

    /** Android rehbere kişi ekle */
    fun addCustomerToAndroidContacts(
        activity: Activity,
        customerName: String,
        phoneNumber: String,
        originalName: String? = null,
        company: String? = null
    )
}