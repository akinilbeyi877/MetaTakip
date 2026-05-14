package com.example.metatakip.feature_backup.util

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.*

/**
 * 📊 CSV Dışa Aktarma Yöneticisi
 * Veritabanı tablolarını evrensel CSV formatına dönüştürür.
 */
object CsvExporter {
    private const val TAG = "CsvExporter"

    /**
     * Belirli bir tabloyu OutputStream üzerinden CSV olarak yazar.
     */
    fun exportTable(db: SQLiteDatabase, table: String, out: OutputStream) {
        // 🛠️ HİBRİT KRİTİK: Tüm kolonları (ID, UUID, updatedAt) seçiyoruz.
        val cursor = db.rawQuery("SELECT * FROM $table", null)
        try {
            OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                writeCursorAsCsv(cursor, writer)
            }
            Log.d(TAG, "✅ $table tablosu başarıyla dışa aktarıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CSV dışa aktarma hatası ($table): ${e.message}")
        } finally {
            cursor.close()
        }
    }

    /**
     * Tabloyu doğrudan bir dosyaya yazar.
     */
    fun exportTableToFile(db: SQLiteDatabase, table: String, file: File) {
        try {
            FileOutputStream(file).use { out ->
                exportTable(db, table, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Dosya yazma hatası: ${e.message}")
        }
    }

    /**
     * 📝 Cursor verisini CSV formatına dönüştüren ana motor.
     */
    fun writeCursorAsCsv(cursor: Cursor, w: Writer) {
        val colCount = cursor.columnCount

        // 1. ADIM: Header (Başlık Satırı)
        // Kolon isimlerini virgülle birleştir (Örn: id,uuid,ad,soyad,updatedAt)
        val header = (0 until colCount).joinToString(",") { cursor.getColumnName(it) }
        w.append(header)
        w.append("\n")

        // 2. ADIM: Veri Satırları
        while (cursor.moveToNext()) {
            val row = (0 until colCount).joinToString(",") { i ->
                val value = cursor.getString(i) ?: ""

                // CSV Kaçış Karakteri (Escaping):
                // Veri içinde virgül veya tırnak varsa format bozulmasın diye
                // veriyi çift tırnak içine alıyoruz ve içindeki tırnakları çiftliyoruz.
                val escapedValue = value.replace("\"", "\"\"")
                "\"$escapedValue\""
            }
            w.append(row)
            w.append("\n")
        }
        w.flush()
    }
}