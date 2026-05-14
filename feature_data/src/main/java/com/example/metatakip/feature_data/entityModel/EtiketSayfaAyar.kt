package com.example.metatakip.feature_data.entityModel

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity

data class EtiketSayfaAyar(
    var marginLeft: Int = 16,
    var marginRight: Int = 16,
    var marginTop: Int = 16,
    var marginBottom: Int = 16,
    var textSizeSp: Float = 12f,
    var textColor: Int = Color.BLACK,
    var typeface: Typeface = Typeface.MONOSPACE,
    var gravity: Int = Gravity.START,
    // ─── Etiket fiziksel boyut ayarları (HereLabel stili) ───
    var labelName: String = "",
    var widthMm: Float = 100.0f,
    var heightMm: Float = 80.0f,
    var columns: Int = 1,
    var spacingMm: Float = 0.0f
)