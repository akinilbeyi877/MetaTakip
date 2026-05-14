package com.example.metatakip.feature.label.data

// File: EtiketSablonDaoImpl.kt

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.label.EtiketManager
import com.example.metatakip.feature_data.entityModel.EtiketSablon
import com.example.metatakip.feature_data.entityModel.EtiketSayfaAyar
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface

class EtiketSablonDaoImpl(context: Context) : EtiketSablonDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "EtiketSablonDao"

    // ============================================================
    // 🆕 ŞABLON OLUŞTUR
    // ============================================================
    override fun createSablon(
        userId: Int,
        adi: String,
        firmaId: Long,
        firmaUuid: String,
        varsayilan: Boolean
    ): Long {
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
            // Yeni firma alanlari — kolon yoksa ContentValues'da zararsizdir; kolon varsa dolar.
            if (columnExists(db, "etiket_sablon", "firma_id")) put("firma_id", firmaId)
            if (columnExists(db, "etiket_sablon", "firma_uuid")) put("firma_uuid", firmaUuid)
        }

        val id = db.insert("etiket_sablon", null, values)
        Log.i(TAG, "✅ Şablon oluşturuldu id=$id firmaId=$firmaId")
        return id
    }

    private fun columnExists(db: android.database.sqlite.SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        try {
            while (c.moveToNext()) if (c.getString(1) == column) return true
        } finally { c.close() }
        return false
    }

    /** firma_uuid bos olan etiket_sablon satirlarini firma_id uzerinden firma tablosundan doldurur. */
    private fun backfillFirmaUuid(db: android.database.sqlite.SQLiteDatabase) {
        if (!columnExists(db, "etiket_sablon", "firma_uuid") ||
            !columnExists(db, "etiket_sablon", "firma_id")) return
        try {
            val n = db.compileStatement(
                "UPDATE etiket_sablon SET firma_uuid = " +
                "(SELECT uuid FROM firma WHERE firma.id = etiket_sablon.firma_id) " +
                "WHERE (firma_uuid IS NULL OR firma_uuid = '') AND firma_id > 0"
            ).use { it.executeUpdateDelete() }
            if (n > 0) Log.i(TAG, "🔧 backfillFirmaUuid: $n satira UUID basildi")
        } catch (e: Exception) {
            Log.w(TAG, "backfillFirmaUuid hata: " + e.message)
        }
    }

    // ============================================================
    // 📋 TÜM KULLANICILARIN ŞABLONLARINI GETİR (parametresiz)
    // ============================================================
    override fun getAllSablonlar(): List<EtiketSablon> {
        val db = dbHelper.writableDatabase
        backfillFirmaUuid(db)
        val list = mutableListOf<EtiketSablon>()
        val hasFirmaId   = columnExists(db, "etiket_sablon", "firma_id")
        val hasFirmaUuid = columnExists(db, "etiket_sablon", "firma_uuid")
        val firmaCols = (if (hasFirmaId) ", s.firma_id" else ", 0 AS firma_id") +
                        (if (hasFirmaUuid) ", s.firma_uuid" else ", '' AS firma_uuid")
        // LEFT JOIN firma — once UUID, sonra ID ile
        val joinClause = when {
            hasFirmaUuid && hasFirmaId -> "LEFT JOIN firma f ON (f.uuid = s.firma_uuid AND s.firma_uuid <> '') OR f.id = s.firma_id"
            hasFirmaUuid -> "LEFT JOIN firma f ON f.uuid = s.firma_uuid AND s.firma_uuid <> ''"
            hasFirmaId   -> "LEFT JOIN firma f ON f.id = s.firma_id"
            else -> ""
        }

        val c = db.rawQuery(
            "SELECT s.id, s.user_id, s.adi, s.varsayilan, s.createdAt" + firmaCols +
            ", COALESCE(f.firmaAdi, '') AS firma_adi_joined" +
            " FROM etiket_sablon s " + joinClause +
            " ORDER BY s.createdAt DESC",
            null
        )

        while (c.moveToNext()) {
            list.add(
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4),
                    firmaId   = c.getLong(5),
                    firmaUuid = c.getString(6) ?: "",
                    firmaAdi  = c.getString(7) ?: ""
                )
            )
        }
        c.close()
        Log.d(TAG, "📋 Tüm şablonlar getirildi: ${list.size} adet")
        return list
    }

    // ============================================================
    // 📋 TÜM ŞABLONLARI GETİR (userId)
    // ============================================================
    override fun getAllSablonlar(userId: Int): List<EtiketSablon> {
        val db = dbHelper.writableDatabase
        backfillFirmaUuid(db)
        val list = mutableListOf<EtiketSablon>()
        val hasFirmaId   = columnExists(db, "etiket_sablon", "firma_id")
        val hasFirmaUuid = columnExists(db, "etiket_sablon", "firma_uuid")
        val firmaCols = (if (hasFirmaId) ", s.firma_id" else ", 0 AS firma_id") +
                        (if (hasFirmaUuid) ", s.firma_uuid" else ", '' AS firma_uuid")
        val joinClause = when {
            hasFirmaUuid && hasFirmaId -> "LEFT JOIN firma f ON (f.uuid = s.firma_uuid AND s.firma_uuid <> '') OR f.id = s.firma_id"
            hasFirmaUuid -> "LEFT JOIN firma f ON f.uuid = s.firma_uuid AND s.firma_uuid <> ''"
            hasFirmaId   -> "LEFT JOIN firma f ON f.id = s.firma_id"
            else -> ""
        }

        val c = db.rawQuery(
            "SELECT s.id, s.user_id, s.adi, s.varsayilan, s.createdAt" + firmaCols +
            ", COALESCE(f.firmaAdi, '') AS firma_adi_joined" +
            " FROM etiket_sablon s " + joinClause +
            " WHERE s.user_id = ? ORDER BY s.varsayilan DESC, s.createdAt DESC",
            arrayOf(userId.toString())
        )

        while (c.moveToNext()) {
            list.add(
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4),
                    firmaId   = c.getLong(5),
                    firmaUuid = c.getString(6) ?: "",
                    firmaAdi  = c.getString(7) ?: ""
                )
            )
        }
        c.close()
        Log.i(TAG, "📋 getAllSablonlar(userId=$userId): ${list.size} adet — firma JOIN aktif")
        list.forEach { Log.d(TAG, "   • id=${it.id} adi='${it.adi}' firmaId=${it.firmaId} firmaUuid='${it.firmaUuid}' firmaAdi='${it.firmaAdi}'") }
        return list
    }

    // ============================================================
    // 🔎 ID İLE GETİR
    // ============================================================
    override fun getSablonById(id: Long): EtiketSablon? {
        val db = dbHelper.readableDatabase
        val hasFirmaId   = columnExists(db, "etiket_sablon", "firma_id")
        val hasFirmaUuid = columnExists(db, "etiket_sablon", "firma_uuid")
        val firmaCols = (if (hasFirmaId) ", s.firma_id" else ", 0 AS firma_id") +
                        (if (hasFirmaUuid) ", s.firma_uuid" else ", '' AS firma_uuid")
        val joinClause = when {
            hasFirmaUuid && hasFirmaId -> "LEFT JOIN firma f ON (f.uuid = s.firma_uuid AND s.firma_uuid <> '') OR f.id = s.firma_id"
            hasFirmaUuid -> "LEFT JOIN firma f ON f.uuid = s.firma_uuid AND s.firma_uuid <> ''"
            hasFirmaId   -> "LEFT JOIN firma f ON f.id = s.firma_id"
            else -> ""
        }
        val c = db.rawQuery(
            "SELECT s.id, s.user_id, s.adi, s.varsayilan, s.createdAt" + firmaCols +
            ", COALESCE(f.firmaAdi, '') AS firma_adi_joined" +
            " FROM etiket_sablon s " + joinClause +
            " WHERE s.id = ? LIMIT 1",
            arrayOf(id.toString())
        )

        val sablon =
            if (c.moveToFirst()) {
                EtiketSablon(
                    id = c.getLong(0),
                    userId = c.getInt(1),
                    adi = c.getString(2),
                    varsayilan = c.getInt(3) == 1,
                    createdAt = c.getLong(4),
                    firmaId   = c.getLong(5),
                    firmaUuid = c.getString(6) ?: "",
                    firmaAdi  = c.getString(7) ?: ""
                )
            } else null

        c.close()
        return sablon
    }

    // ============================================================
    // ⭐ VARSAYILAN YAP
    // ============================================================
    override fun setVarsayilanSablon(userId: Int, sablonId: Long): Boolean {
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
    override fun deleteSablon(sablonId: Long) {
        val db = dbHelper.writableDatabase
        db.delete("etiket_sablon", "id = ?", arrayOf(sablonId.toString()))
    }

    // ============================================================
    // 💾 BİLEŞENLER
    // ============================================================
    override fun saveBilesenler(
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

    override fun loadBilesenSecimleri(
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
    override fun saveSayfaAyar(sablonId: Long, ayar: EtiketSayfaAyar) {
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

    override fun loadSayfaAyar(sablonId: Long): EtiketSayfaAyar? {
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
    // 📝 YAZDIRMA METNI (manuel + bilesenli) — DB'ye kayit + change_log ile sync
    // ============================================================
    /** UPSERT: row varsa UPDATE; yoksa INSERT. Etkilenen satir sayisini doner. */
    fun saveTemplateText(sablonId: Long, isManual: Boolean, text: String): Int {
        val db = dbHelper.writableDatabase
        val col = if (isManual) "manual_text" else "comp_text"

        // Once row var mi kontrol et
        val cur = db.rawQuery("SELECT id FROM etiket_sablon WHERE id = ? LIMIT 1", arrayOf(sablonId.toString()))
        val rowExists = cur.moveToFirst()
        cur.close()

        val v = ContentValues().apply {
            put(col, text)
            put("updatedAt", System.currentTimeMillis() / 1000)
        }

        val n: Int = if (rowExists) {
            db.update("etiket_sablon", v, "id = ?", arrayOf(sablonId.toString()))
        } else {
            // YOK → kayit olustur (varsayilan degerlerle)
            v.put("id", sablonId)
            v.put("user_id", 0)
            v.put("adi", "Varsayilan Etiket")
            v.put("varsayilan", 1)
            v.put("createdAt", System.currentTimeMillis() / 1000)
            val newId = db.insert("etiket_sablon", null, v)
            Log.w(TAG, "⚠️ Row yoktu, INSERT edildi sablonId=$sablonId newId=$newId")
            if (newId > 0) 1 else 0
        }
        Log.i(TAG, "═══ DAO.saveTemplateText ═══")
        Log.i(TAG, "    sablonId=$sablonId col=$col rows=$n exists=$rowExists len=${text.length}")
        Log.i(TAG, "    text.preview=\"" + text.take(60) + "\"")

        // Verify-back: gercekten DB'ye yazildi mi?
        try {
            val vc = db.rawQuery("SELECT $col FROM etiket_sablon WHERE id = ?", arrayOf(sablonId.toString()))
            if (vc.moveToFirst()) {
                val storedRaw = vc.getString(0)
                val stored = storedRaw ?: ""
                Log.i(TAG, "    ✓ VERIFY-BACK col='$col' DB'de var → len=" + stored.length + " preview=\"" + stored.take(60) + "\"")
                if (stored != text) Log.e(TAG, "    ✗ VERIFY-BACK MISMATCH! kaydedildigi sanilan != DB'deki")
            } else {
                Log.e(TAG, "    ✗ VERIFY-BACK row YOK! sablonId=$sablonId")
            }
            vc.close()
        } catch (e: Exception) {
            Log.e(TAG, "    ✗ VERIFY-BACK hata: " + e.message)
        }
        Log.i(TAG, "═══ DAO.saveTemplateText bitti ═══")

        // UPDATE trigger eski cihazda yoksa garanti olsun diye change_log'a manuel insert
        try {
            val det = "{\"id\":" + sablonId + ",\"col\":\"" + col + "\",\"table\":\"etiket_sablon\",\"updatedAt\":" + System.currentTimeMillis() + "}"
            val cv = ContentValues().apply {
                put("table_name", "etiket_sablon")
                put("action_type", "UPDATE")
                put("record_id", sablonId)
                put("changed_at", System.currentTimeMillis() / 1000)
                put("details", det)
                put("synced", 0)
            }
            db.insert("change_log", null, cv)
        } catch (e: Exception) {
            Log.w(TAG, "change_log yazilamadi: " + e.message)
        }
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
