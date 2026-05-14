package com.example.metatakip.feature_data.helpers

import android.content.Intent
import android.widget.TextView

interface IGenericListHelper {

    /** Yazdırma akışını başlatır: izin → BT aç → cihaz seç → yazdır */
    fun startPrintFlow(text: String)

    /** Activity.onRequestPermissionsResult içinde delegasyon */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray)

    /** Activity.onActivityResult içinde delegasyon */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent? = null)

    /** Dış linkler */
    fun openWhatsAppWeb()
    fun openGoogleDrive()

    /** UI helper */
    fun showColorPicker(textView: TextView)
}