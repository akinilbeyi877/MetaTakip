package com.example.metatakip.feature_backup.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🕒 Zaman ve Tarih Yardımcısı
 * Yedekleme dosyaları ve senkronizasyon logları için standart formatlar sunar.
 */
object TimeUtils {

    /**
     * Dosya isimleri için benzersiz bir zaman damgası üretir.
     * Örn: 20260419_210858_123 (Sıralama için yıl-ay-gün-saat-dakika-saniye-milisaniye)
     */
    fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())

    /**
     * Yerel yedek klasörü için bugünün tarihini döner.
     * Örn: 2026-04-19
     */
    fun todayFolder(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * Long (milisaniye) tipindeki zamanı okunabilir metne çevirir.
     * UI ekranlarında ve loglarda kullanılır.
     */
    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "Bilinmiyor"
        return SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    /**
     * Sadece saat ve dakika döner.
     * Otomatik yedekleme saatleri kontrolünde kullanılır.
     */
    fun formatOnlyTime(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}