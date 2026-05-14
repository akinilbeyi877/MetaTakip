package com.example.metatakip.data.metaTakipDb.crud

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.CallRecord

class CallLogsDao(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "CallLogsDao"

    // ============================================================
    // ➕ CREATE (TRANSACTION’LI)
    // ============================================================
    fun addCallLog(callLog: CallRecord): Long {
        val db = dbHelper.writableDatabase
        return try {
            db.beginTransaction()

            val values = ContentValues().apply {
                put("uuid", callLog.uuid?.takeIf { it.isNotBlank() } ?: generateUuid())
                put("musteriTelefonu", callLog.musteriTelefonu)
                put("musteriAdi", callLog.musteriAdi)
                put("arananFirmaAdi", callLog.arananFirmaAdi)
                put("arananHatAdi", callLog.arananHatAdi)
                put("arananTelefon", callLog.arananTelefon)

                put("cihazAdi", callLog.cihazAdi)
                put("cihazFirmaAdi", callLog.cihazFirmaAdi ?: "Yapılandırılmamış")
                put("cihazKullaniciAdi", callLog.cihazKullaniciAdi ?: "Bilinmiyor")
                put("cihazRolu", callLog.cihazRolu)
                put("cihazMerkezMi", if (callLog.cihazMerkezMi) 1 else 0)
                put("simYuvasi", callLog.simYuvasi)

                put("cagriTuru", callLog.cagriTuru)
                put("cagriZamani", callLog.cagriZamani ?: (System.currentTimeMillis() / 1000))

                put("merkezeIletildiMi", 0)
                put("createdAt", System.currentTimeMillis() / 1000)
                put("updatedAt", System.currentTimeMillis() / 1000)
            }

            val id = db.insert("call_logs", null, values)
            if (id != -1L) {
                db.setTransactionSuccessful()
                Log.d(TAG, "✅ Çağrı eklendi ID=$id Tür=${callLog.cagriTuru} Tel=${callLog.musteriTelefonu}")
            } else {
                Log.e(TAG, "❌ Çağrı eklenemedi")
            }
            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ addCallLog hatası", e)
            -1L
        } finally {
            db.endTransaction()
            try {
                context.sendBroadcast(android.content.Intent("com.example.metatakip.CALL_LOG_ADDED"))
            } catch (_: Exception) {}
        }
    }

    // ============================================================
    // 📋 READ ALL
    // ============================================================
    fun getAllCallLogs(): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM call_logs ORDER BY cagriZamani DESC",
            null
        )

        try {
            while (cursor.moveToNext()) {
                list.add(mapCursorToCallRecord(cursor))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ getAllCallLogs hatası", e)
        } finally {
            cursor.close()
        }
        return list
    }

    // ============================================================
    // 🔄 SENKRON EDİLMEYENLER
    // ============================================================
    fun getUnsyncedCallLogs(): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM call_logs WHERE merkezeIletildiMi = 0 ORDER BY cagriZamani ASC",
            null
        )

        try {
            while (cursor.moveToNext()) {
                list.add(mapCursorToCallRecord(cursor))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ getUnsyncedCallLogs hatası", e)
        } finally {
            cursor.close()
        }
        return list
    }

    // ============================================================
    // ✅ SENKRON İŞARETLE (TRANSACTION)
    // ============================================================
    fun markAsSynced(callLogId: Long): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            db.beginTransaction()

            val values = ContentValues().apply {
                put("merkezeIletildiMi", 1)
                put("merkezeIletilmeZamani", System.currentTimeMillis() / 1000)
                putNull("merkezHataMesaji")
            }

            val rows = db.update(
                "call_logs",
                values,
                "id=?",
                arrayOf(callLogId.toString())
            )

            if (rows > 0) {
                db.setTransactionSuccessful()
                Log.d(TAG, "✅ Senkronlandı ID=$callLogId")
            }

            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ markAsSynced hatası", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    // ============================================================
    // 🔍 ID İLE GETİR
    // ============================================================
    fun getCallLogById(id: Long): CallRecord? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM call_logs WHERE id=?",
            arrayOf(id.toString())
        )

        val result = if (cursor.moveToFirst()) mapCursorToCallRecord(cursor) else null
        cursor.close()
        return result
    }

    // ============================================================
    // 📞 TELEFON İLE GETİR
    // ============================================================
    fun getCallLogsByPhone(phone: String): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT * FROM call_logs
            WHERE musteriTelefonu LIKE ? OR arananTelefon LIKE ?
            ORDER BY cagriZamani DESC
            """,
            arrayOf("%$phone%", "%$phone%")
        )

        try {
            while (cursor.moveToNext()) {
                list.add(mapCursorToCallRecord(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    // ============================================================
    // 📅 TARİH ARALIĞI
    // ============================================================
    fun getCallLogsByDateRange(startDate: Long, endDate: Long): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT * FROM call_logs
            WHERE cagriZamani BETWEEN ? AND ?
            ORDER BY cagriZamani DESC
            """,
            arrayOf(startDate.toString(), endDate.toString())
        )

        try {
            while (cursor.moveToNext()) {
                list.add(mapCursorToCallRecord(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    // ============================================================
    // ❌ SİL (GERÇEK)
    // ============================================================
    fun deleteCallLog(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("call_logs", "id=?", arrayOf(id.toString()))
        return rows > 0
    }

    // ============================================================
    // 🧹 TÜM KAYITLARI TEMİZLE
    // ============================================================
    fun deleteAllCallLogs(): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("call_logs", null, null)
        Log.d(TAG, "🗑️ Tüm çağrı kayıtları silindi ($rows adet)")
        return true
    }

    // ============================================================
    // 🧹 ESKİ KAYITLARI TEMİZLE
    // ============================================================
    fun deleteOldCallLogs(days: Int): Int {
        val db = dbHelper.writableDatabase
        val cutoff = (System.currentTimeMillis() / 1000) - (days * 86400)

        val rows = db.delete(
            "call_logs",
            "cagriZamani < ? AND merkezeIletildiMi = 1",
            arrayOf(cutoff.toString())
        )

        Log.d(TAG, "🗑️ $rows eski çağrı silindi")
        return rows
    }

    // ============================================================
    // 📊 İSTATİSTİKLER
    // ============================================================
    fun getTotalCallCount(): Int {
        val db = dbHelper.readableDatabase
        val c = db.rawQuery("SELECT COUNT(*) FROM call_logs", null)
        val count = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return count
    }

    fun getCallStatsByDevice(): List<Triple<String, String, Int>> {
        val list = mutableListOf<Triple<String, String, Int>>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT cihazAdi, cihazRolu, COUNT(*) AS toplam
            FROM call_logs
            GROUP BY cihazAdi, cihazRolu
            ORDER BY toplam DESC
            """,
            null
        )

        while (cursor.moveToNext()) {
            list.add(
                Triple(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getInt(2)
                )
            )
        }
        cursor.close()
        return list
    }

    fun getCallStatsByType(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT cagriTuru, COUNT(*) FROM call_logs GROUP BY cagriTuru",
            null
        )

        while (cursor.moveToNext()) {
            map[cursor.getString(0)] = cursor.getInt(1)
        }
        cursor.close()
        return map
    }

    // ============================================================
    // ============================================================
    // 🌐 UUID GENERATOR
    // ============================================================
    private fun generateUuid(): String = java.util.UUID.randomUUID().toString()

    // ============================================================
    // 🧩 CURSOR → MODEL (NULL-SAFE)
    // ============================================================
    private fun mapCursorToCallRecord(cursor: Cursor): CallRecord {

        fun s(col: String): String? {
            val i = cursor.getColumnIndex(col)
            return if (i != -1 && !cursor.isNull(i)) cursor.getString(i) else null
        }

        fun l(col: String): Long? {
            val i = cursor.getColumnIndex(col)
            return if (i != -1 && !cursor.isNull(i)) cursor.getLong(i) else null
        }

        fun i(col: String): Int {
            val i = cursor.getColumnIndex(col)
            return if (i != -1) cursor.getInt(i) else 0
        }

        return CallRecord(
            id = l("id") ?: 0L,
            uuid = s("uuid"),
            musteriTelefonu = s("musteriTelefonu") ?: "",
            musteriAdi = s("musteriAdi"),
            arananFirmaAdi = s("arananFirmaAdi") ?: "",
            arananHatAdi = s("arananHatAdi") ?: "",
            arananTelefon = s("arananTelefon") ?: "",
            cihazAdi = s("cihazAdi") ?: "",
            cihazFirmaAdi = s("cihazFirmaAdi") ?: "Yapılandırılmamış",
            cihazKullaniciAdi = s("cihazKullaniciAdi") ?: "Bilinmiyor",
            cihazRolu = s("cihazRolu") ?: "SAHA",
            cihazMerkezMi = i("cihazMerkezMi") == 1,
            simYuvasi = s("simYuvasi") ?: "SIM1",
            cagriTuru = s("cagriTuru") ?: "GELEN",
            cagriZamani = l("cagriZamani"),
            merkezeIletildiMi = i("merkezeIletildiMi") == 1,
            merkezeIletilmeZamani = l("merkezeIletilmeZamani"),
            merkezHataMesaji = s("merkezHataMesaji"),
            createdAt = l("createdAt")
        )
    }
}
