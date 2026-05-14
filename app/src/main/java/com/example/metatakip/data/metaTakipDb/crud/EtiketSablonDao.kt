package com.example.metatakip.data.metaTakipDb.crud

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.label.EtiketManager
import com.example.metatakip.feature_data.entityModel.EtiketSablon
import com.example.metatakip.feature_data.entityModel.EtiketSayfaAyar

class EtiketSablonDao(context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "EtiketSablonDao"

    // ============================================================
    // 🆕 ŞABLON OLUŞTUR
    // ============================================================
    fun createSablon(userId: Int, adi: String, varsayilan: Boolean = false): Long {
        val db = dbHelper.writableDatabase

        if (varsayilan) {
            db.execSQL(
                "UPDATE etiket_sablon SET varsayilan = 0 WHERE user_id = ?",
                arrayOf(userId)
            )
        }

        val values = ContentValues().apply {
            put("user_id", userId)
            put("adi", adi)
            put("varsayilan", if (varsayilan) 1 else 0)
        }

        val id = db.insert("etiket_sablon", null, values)
        Log.i(TAG, "✅ Şablon oluşturuldu id=$id")
        return id
    }


    // ============================================================
    // 📋 TÜM KULLANICILARIN ŞABLONLARINI GETİR (YENİ - parametresiz)
    // ============================================================
    fun getAllSablonlar(): List<EtiketSablon> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<EtiketSablon>()

        val c = db.rawQuery(
            """
            SELECT id, user_id, adi, varsayilan, createdAt
            FROM etiket_sablon
            ORDER BY createdAt DESC
            """,
            null  // WHERE koşulu yok, tüm kayıtlar
        )

        while (c.moveToNext()) {
            list.add(
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4)
                )
            )
        }
        c.close()
        Log.d(TAG, "📋 Tüm şablonlar getirildi: ${list.size} adet")
        return list
    }
    // ============================================================
    // 📋 TÜM ŞABLONLARI GETİR
    // ============================================================
    fun getAllSablonlar(userId: Int): List<EtiketSablon> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<EtiketSablon>()

        val c = db.rawQuery(
            """
            SELECT id, user_id, adi, varsayilan, createdAt
            FROM etiket_sablon
            WHERE user_id = ?
            ORDER BY varsayilan DESC, createdAt DESC
            """,
            arrayOf(userId.toString())
        )

        while (c.moveToNext()) {
            list.add(
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4)
                )
            )
        }
        c.close()
        return list
    }

    // ============================================================
    // 🔎 ID İLE GETİR
    // ============================================================
    fun getSablonById(id: Long): EtiketSablon? {
        val db = dbHelper.readableDatabase
        val c = db.rawQuery(
            """
            SELECT id, user_id, adi, varsayilan, createdAt
            FROM etiket_sablon
            WHERE id = ?
            LIMIT 1
            """,
            arrayOf(id.toString())
        )

        val sablon =
            if (c.moveToFirst()) {
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4)
                )
            } else null

        c.close()
        return sablon
    }

    // ============================================================
    // ⭐ VARSAYILAN YAP
    // ============================================================
    fun setVarsayilanSablon(userId: Int, sablonId: Long): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            db.execSQL(
                "UPDATE etiket_sablon SET varsayilan = 0 WHERE user_id = ?",
                arrayOf(userId)
            )

            val v = ContentValues().apply { put("varsayilan", 1) }
            db.update(
                "etiket_sablon",
                v,
                "id = ?",
                arrayOf(sablonId.toString())
            )

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Varsayılan ayarlanamadı", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    // ============================================================
    // 🗑️ SİL
    // ============================================================
    fun deleteSablon(sablonId: Long) {
        val db = dbHelper.writableDatabase
        db.delete("etiket_sablon", "id = ?", arrayOf(sablonId.toString()))
    }

    // ============================================================
    // 💾 BİLEŞENLER
    // ============================================================
    fun saveBilesenler(
        sablonId: Long,
        bilesenler: List<EtiketManager.EtiketBileseni>
    ) {
        val db = dbHelper.writableDatabase
        db.delete("etiket_sablon_bilesen", "sablon_id = ?", arrayOf(sablonId.toString()))

        bilesenler.forEachIndexed { index, b ->
            val v = ContentValues().apply {
                put("sablon_id", sablonId)
                put("bilesen_id", b.id)
                put("secili", if (b.secili) 1 else 0)
                put("sira", index)
            }
            db.insert("etiket_sablon_bilesen", null, v)
        }
    }

    fun loadBilesenSecimleri(
        sablonId: Long,
        all: MutableList<EtiketManager.EtiketBileseni>
    ) {
        val db = dbHelper.readableDatabase
        val c = db.rawQuery(
            "SELECT bilesen_id, secili FROM etiket_sablon_bilesen WHERE sablon_id = ?",
            arrayOf(sablonId.toString())
        )

        val map = mutableMapOf<String, Boolean>()
        while (c.moveToNext()) {
            map[c.getString(0)] = c.getInt(1) == 1
        }
        c.close()

        all.forEach { map[it.id]?.let { sec -> it.secili = sec } }
    }

    // ============================================================
    // 🎨 SAYFA AYAR
    // ============================================================
    fun saveSayfaAyar(sablonId: Long, ayar: EtiketSayfaAyar) {
        val db = dbHelper.writableDatabase
        db.delete("etiket_sayfa_ayar", "sablon_id = ?", arrayOf(sablonId.toString()))

        val v = ContentValues().apply {
            put("sablon_id", sablonId)
            put("paddingTop", ayar.marginTop)
            put("paddingBottom", ayar.marginBottom)
            put("paddingLeft", ayar.marginLeft)
            put("paddingRight", ayar.marginRight)
            put("textSize", ayar.textSizeSp)
            put("textColor", ayar.textColor)
        }
        db.insert("etiket_sayfa_ayar", null, v)
    }

    fun loadSayfaAyar(sablonId: Long): EtiketSayfaAyar? {
        val db = dbHelper.readableDatabase
        val c = db.rawQuery(
            """
            SELECT paddingTop, paddingBottom, paddingLeft, paddingRight, textSize, textColor
            FROM etiket_sayfa_ayar
            WHERE sablon_id = ?
            LIMIT 1
            """,
            arrayOf(sablonId.toString())
        )

        val ayar =
            if (c.moveToFirst()) {
                EtiketSayfaAyar(
                    marginTop = c.getInt(0),
                    marginBottom = c.getInt(1),
                    marginLeft = c.getInt(2),
                    marginRight = c.getInt(3),
                    textSizeSp = c.getFloat(4),
                    textColor = c.getInt(5)
                )
            } else null

        c.close()
        return ayar
    }

    // ============================================================
    // 📝 YAZDIRMA METNI (manuel + bilesenli)
    // ============================================================
    fun saveTemplateText(sablonId: Long, isManual: Boolean, text: String): Int {
        val db = dbHelper.writableDatabase
        val col = if (isManual) "manual_text" else "comp_text"
        val cur = db.rawQuery("SELECT id FROM etiket_sablon WHERE id = ? LIMIT 1", arrayOf(sablonId.toString()))
        val rowExists = cur.moveToFirst(); cur.close()
        val v = ContentValues().apply {
            put(col, text)
            put("updatedAt", System.currentTimeMillis() / 1000)
        }
        val n: Int = if (rowExists) {
            db.update("etiket_sablon", v, "id = ?", arrayOf(sablonId.toString()))
        } else {
            v.put("id", sablonId); v.put("user_id", 0); v.put("adi", "Varsayilan Etiket")
            v.put("varsayilan", 1); v.put("createdAt", System.currentTimeMillis() / 1000)
            if (db.insert("etiket_sablon", null, v) > 0) 1 else 0
        }
        try {
            val det = "{\"id\":" + sablonId + ",\"col\":\"" + col + "\",\"table\":\"etiket_sablon\"}"
            val cv = ContentValues().apply {
                put("table_name", "etiket_sablon"); put("action_type", "UPDATE")
                put("record_id", sablonId); put("changed_at", System.currentTimeMillis() / 1000)
                put("details", det); put("synced", 0)
            }
            db.insert("change_log", null, cv)
        } catch (_: Exception) {}
        return n
    }

    fun loadTemplateText(sablonId: Long, isManual: Boolean): String? {
        val db = dbHelper.readableDatabase
        val col = if (isManual) "manual_text" else "comp_text"
        val c = db.rawQuery("SELECT $col FROM etiket_sablon WHERE id = ? LIMIT 1", arrayOf(sablonId.toString()))
        var s: String? = null
        if (c.moveToFirst()) s = c.getString(0)
        c.close()
        return if (s.isNullOrBlank()) null else s
    }
}
