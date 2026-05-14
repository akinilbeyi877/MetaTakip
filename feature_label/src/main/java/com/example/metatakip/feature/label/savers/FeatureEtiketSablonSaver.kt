package com.example.metatakip.feature.label.savers

import android.content.Context
import android.util.Log
import com.example.metatakip.feature.label.data.EtiketSablonDaoImpl
import com.example.metatakip.feature_data.entityModel.SessionManager
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface

class FeatureEtiketSablonSaver(
    private val context: Context,
    private val dao: EtiketSablonDaoInterface = EtiketSablonDaoImpl(context)
) {

    fun canHandle(table: String): Boolean {
        return table.equals("etiket_sablon", ignoreCase = true)
    }

    fun save(
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Long {
        return try {
            val adi = data["adi"]?.toString()?.trim().orEmpty()
            if (adi.isBlank()) return -1L

            val firmaId = when (val raw = data["firma_id"]) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull() ?: 0L
                else -> 0L
            }

            // firma_uuid: HER ZAMAN firma_id'den DB'den cek (form hidden field dropdown degisince guncellenmez)
            // Eger firma_id varsa firmaUuid'i firma tablosundan zorla yenile.
            val formUuid = data["firma_uuid"]?.toString()?.trim().orEmpty()
            val dbUuid   = lookupFirmaUuid(firmaId)
            val firmaUuid = when {
                firmaId > 0L && dbUuid.isNotEmpty() -> dbUuid          // DB her zaman gercektir
                formUuid.isNotEmpty() -> formUuid                       // firmaId yoksa formdan
                else -> ""
            }
            android.util.Log.i("FeatureEtiketSablonSaver",
                "🔑 firmaId=$firmaId formUuid='$formUuid' dbUuid='$dbUuid' → kullanilan='$firmaUuid'")

            val session = SessionManager(context)

            if (editMode && recordId > 0L) {
                // GUNCELLE — DAO'da updateSablon yoksa direkt SQL
                val db = (dao as? com.example.metatakip.feature.label.data.EtiketSablonDaoImpl)
                    ?.let { android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                        context.getDatabasePath("MetaTakip.db"), null) }
                    ?: throw IllegalStateException("DB acilamadi")
                val v = android.content.ContentValues().apply {
                    put("adi", adi)
                    put("firma_id", firmaId)
                    put("firma_uuid", firmaUuid)
                    put("updatedAt", System.currentTimeMillis() / 1000)
                }
                val n = db.update("etiket_sablon", v, "id = ?", arrayOf(recordId.toString()))
                // change_log → bulut sync
                try {
                    val cv = android.content.ContentValues().apply {
                        put("table_name", "etiket_sablon")
                        put("action_type", "UPDATE")
                        put("record_id", recordId)
                        put("changed_at", System.currentTimeMillis() / 1000)
                        put("details", "{\"id\":" + recordId + ",\"firmaId\":" + firmaId + "}")
                        put("synced", 0)
                    }
                    db.insert("change_log", null, cv)
                } catch (_: Exception) {}
                db.close()
                Log.i("FeatureEtiketSablonSaver", "✅ Sablon guncellendi id=$recordId firmaId=$firmaId rows=$n")
                recordId
            } else {
                dao.createSablon(
                    userId    = session.userId.toInt(),
                    adi       = adi,
                    firmaId   = firmaId,
                    firmaUuid = firmaUuid,
                    varsayilan = false
                ).also {
                    Log.i("FeatureEtiketSablonSaver", "✅ INSERT yeni sablon id=$it firmaId=$firmaId firmaUuid='$firmaUuid'")
                }
            }
        } catch (e: Exception) {
            Log.e("FeatureEtiketSablonSaver", "❌ Etiket şablonu kaydedilemedi", e)
            -1L
        }
    }

    /** firma tablosundan uuid'yi çeker — yerel SQLite'a doğrudan erişir */
    private fun lookupFirmaUuid(firmaId: Long): String {
        if (firmaId <= 0L) return ""
        var db: android.database.sqlite.SQLiteDatabase? = null
        return try {
            val path = context.getDatabasePath("MetaTakip.db").absolutePath
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val c = db.rawQuery("SELECT uuid FROM firma WHERE id = ? LIMIT 1", arrayOf(firmaId.toString()))
            val uuid = if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            c.close()
            uuid
        } catch (e: Exception) {
            Log.w("FeatureEtiketSablonSaver", "firmaUuid lookup başarısız: ${e.message}")
            ""
        } finally {
            db?.close()
        }
    }
}
