package com.example.metatakip.feature_backup.firebase

import com.example.metatakip.feature_backup.util.TimeUtils

/**
 * 📡 Firebase üzerinden gelen canlı senkronizasyon olaylarını temsil eder.
 * Diğer cihazlardan gelen 'Yeni veri var!' sinyalinin içindeki özet bilgilerdir.
 */
data class RealtimeSyncEvent(
    val eventId: String = "",       // Firestore doküman ID'si
    val groupId: String = "",       // Senkronizasyon grubu (Şube/İşletme kodu)
    val deviceId: String = "",      // Gönderen cihazın tekil UUID'si
    val deviceName: String = "",    // Gönderen cihazın adı (Örn: "Kasa-1")
    val fileName: String = "",      // Drive'da oluşturulan ZIP dosyasının adı
    val backupType: String = "INSTANT", // INSTANT, PARTIAL veya FULL
    val changedAt: Long = 0L,       // Olayın gerçekleştiği milisaniye timestamp
    val changeCount: Int = 0,       // Paketin içindeki toplam satır değişikliği
    val tableNames: List<String> = emptyList(), // Hangi tablolarda değişiklik olduğu

    // 🌍 HİBRİT EKSTRA: Cihazın uygulama versiyonu veya senkron sürümü
    // (Farklı versiyonlardaki cihazların birbirinin verisini bozmasını önlemek için)
    val appVersion: Int = 0
) {
    /**
     * ⏱️ Olayın gerçekleşme zamanını okunabilir formatta döner.
     */
    fun getFormattedTime(): String {
        return if (changedAt > 0L) TimeUtils.formatTime(changedAt) else "Bilinmiyor"
    }

    /**
     * 📝 Loglar veya bildirimler için özet mesaj oluşturur.
     */
    fun getSummaryMessage(): String {
        val tablesStr = if (tableNames.isNotEmpty()) {
            " [${tableNames.joinToString(", ")}]"
        } else ""

        return "$deviceName tarafından $changeCount değişiklik yapıldı$tablesStr"
    }

    /**
     * 🛡️ Güvenlik Kontrolü: Olay çok eskiyse işlenmemesi için.
     */
    fun isStale(timeoutMs: Long = 10 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - changedAt) > timeoutMs
    }
}