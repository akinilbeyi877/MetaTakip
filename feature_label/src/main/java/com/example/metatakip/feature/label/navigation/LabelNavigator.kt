package com.example.metatakip.feature.label.navigation

import android.content.Context
import com.example.metatakip.feature_data.label.EtiketManager

class LabelNavigator {

    /** Etiket Şablonları listesinden sağ-click → "Etiket Ayarları Aç" buraya gelir. */
    fun openTemplateEditor(context: Context, sablonId: Long) {
        val etiketManager = EtiketManager(context)
        val controller = LabelFlowController(
            context       = context,
            etiketManager = etiketManager,
            mode          = LabelMode.EDIT,
            onPrint       = null
        )
        controller.setActiveSablon(sablonId)
        controller.showEtiketAyarDialog()   // ← HereLabel stili boyut/ayar dialogu
    }

    /** Bileşen seçimi + yazdırma akışını doğrudan başlat (baskı noktası için). */
    fun openForPrint(context: Context, sablonId: Long, item: Any, onPrint: (String) -> Unit) {
        val etiketManager = EtiketManager(context)
        val controller = LabelFlowController(
            context       = context,
            etiketManager = etiketManager,
            mode          = LabelMode.PRINT,
            onPrint       = onPrint
        )
        controller.setActiveSablon(sablonId)
        controller.start(item)
    }
}