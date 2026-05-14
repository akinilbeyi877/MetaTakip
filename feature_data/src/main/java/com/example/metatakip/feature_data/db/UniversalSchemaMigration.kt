package com.example.metatakip.feature_data.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.UUID

/**
 * v97: Tum is tablolarinda
 *   1) uuid + user_id + firma_uuid kolonlari (idempotent)
 *   2) FK uuid auto-fill triggerlari (musteri.firmaid -> firma_uuid, siparis.musteriId -> musteri_uuid, vb.)
 *   3) change_log triggerlari (INSERT/UPDATE/DELETE) — UUID tabanli sync icin
 *
 * Sonuc: Hangi DAO yazarsa yazsin, hangi yoldan gelirse gelsin INSERT/UPDATE/DELETE
 * islemleri otomatik UUID dolduruyor ve change_log'a yansiyor.
 */
object UniversalSchemaMigration {
    private const val TAG = "UniversalSchema"

    private val BUSINESS_TABLES = listOf(
        "firma", "musteri", "siparis", "urun", "urun_tipi",
        "etiket_sablon", "etiket_sablon_bilesen", "etiket_sayfa_ayar",
        "mesaj_sablon", "personel", "unvan", "call_logs"
    )

    /**
     * FK -> uuid eslemeleri.
     * (table, fk_col, ref_table, uuid_col)
     */
    private val FK_LINKS = listOf(
        FkLink("musteri", "firmaid",            "firma",         "firma_uuid"),
        FkLink("siparis", "firmaid",            "firma",         "firma_uuid"),
        FkLink("siparis", "musteriId",          "musteri",       "musteri_uuid"),
        FkLink("urun",    "siparisId",          "siparis",       "siparis_uuid"),
        FkLink("etiket_sablon", "firma_id",     "firma",         "firma_uuid"),
        FkLink("etiket_sablon_bilesen", "etiket_sablon_id", "etiket_sablon", "etiket_sablon_uuid"),
        FkLink("mesaj_sablon", "firmaid",       "firma",         "firma_uuid"),
        FkLink("personel", "firmaid",           "firma",         "firma_uuid"),
        FkLink("call_logs", "musteriId",        "musteri",       "musteri_uuid")
    )

    private data class FkLink(val table: String, val fkCol: String, val refTable: String, val uuidCol: String)

    fun migrate(db: SQLiteDatabase) {
        Log.i(TAG, "🔧 Universal schema migration basladi (v97)")
        BUSINESS_TABLES.forEach { table ->
            if (!tableExists(db, table)) { Log.d(TAG, "   ⏭ $table yok"); return@forEach }
            ensureUuid(db, table)
            ensureUserId(db, table)
            ensureFirmaLink(db, table)
        }
        installFkTriggers(db)
        installChangeLogTriggers(db)
        installSiparisSeq(db)
        Log.i(TAG, "✅ Universal migration + triggerler tamamlandi")
    }

    private fun ensureUuid(db: SQLiteDatabase, table: String) {
        if (!columnExists(db, table, "uuid")) {
            try { db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT"); Log.i(TAG, "   ➕ $table.uuid") }
            catch (e: Exception) { Log.w(TAG, "uuid ekleme hata $table: ${e.message}") }
        }
        try {
            val c = db.rawQuery("SELECT id FROM $table WHERE uuid IS NULL OR uuid = ''", null)
            db.beginTransaction()
            try {
                while (c.moveToNext()) db.execSQL("UPDATE $table SET uuid = ? WHERE id = ?",
                    arrayOf<Any>(UUID.randomUUID().toString(), c.getLong(0)))
                db.setTransactionSuccessful()
            } finally { db.endTransaction(); c.close() }
        } catch (e: Exception) { Log.w(TAG, "uuid backfill hata $table: ${e.message}") }
        try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_${table}_uuid ON $table(uuid)") }
        catch (e: Exception) { Log.w(TAG, "uuid index hata $table: ${e.message}") }
    }

    private fun ensureUserId(db: SQLiteDatabase, table: String) {
        if (columnExists(db, table, "user_id")) return
        try {
            db.execSQL("ALTER TABLE $table ADD COLUMN user_id INTEGER DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_user_id ON $table(user_id)")
            Log.i(TAG, "   ➕ $table.user_id")
        } catch (e: Exception) { Log.w(TAG, "user_id hata $table: ${e.message}") }
    }

    private fun ensureFirmaLink(db: SQLiteDatabase, table: String) {
        val fkCol = when {
            columnExists(db, table, "firma_id") -> "firma_id"
            columnExists(db, table, "firmaid") -> "firmaid"
            else -> return
        }
        if (columnExists(db, table, "firma_uuid")) return
        try {
            db.execSQL("ALTER TABLE $table ADD COLUMN firma_uuid TEXT")
            db.execSQL("UPDATE $table SET firma_uuid = (SELECT uuid FROM firma WHERE firma.id = $table.$fkCol) WHERE firma_uuid IS NULL OR firma_uuid = ''")
            Log.i(TAG, "   ➕ $table.firma_uuid (FK=$fkCol)")
        } catch (e: Exception) { Log.w(TAG, "firma_uuid hata $table: ${e.message}") }
    }

    /**
     * Her FK icin AFTER INSERT + AFTER UPDATE OF fkCol triggerlari kurar.
     * Trigger, fkCol degisirse uuidCol'u otomatik gunceller.
     * Backfill icin ek olarak uuid kolonu sutununu da doldurur.
     */
    private fun installFkTriggers(db: SQLiteDatabase) {
        FK_LINKS.forEach { link ->
            if (!tableExists(db, link.table) || !tableExists(db, link.refTable)) return@forEach
            // uuidCol kolonu yoksa olustur
            if (!columnExists(db, link.table, link.uuidCol)) {
                try { db.execSQL("ALTER TABLE ${link.table} ADD COLUMN ${link.uuidCol} TEXT")
                    Log.i(TAG, "   ➕ ${link.table}.${link.uuidCol}")
                } catch (e: Exception) { Log.w(TAG, "uuidCol ekleme hata ${link.table}.${link.uuidCol}: ${e.message}") }
            }
            // Mevcut satirlari backfill et
            try {
                db.execSQL("UPDATE ${link.table} SET ${link.uuidCol} = (SELECT uuid FROM ${link.refTable} WHERE ${link.refTable}.id = ${link.table}.${link.fkCol}) WHERE (${link.uuidCol} IS NULL OR ${link.uuidCol} = '') AND ${link.fkCol} IS NOT NULL")
            } catch (e: Exception) { Log.w(TAG, "backfill hata ${link.table}.${link.uuidCol}: ${e.message}") }

            val tInsert = "trg_fk_ins_${link.table}_${link.uuidCol}"
            val tUpdate = "trg_fk_upd_${link.table}_${link.uuidCol}"
            try {
                db.execSQL("DROP TRIGGER IF EXISTS $tInsert")
                db.execSQL("""
                    CREATE TRIGGER $tInsert AFTER INSERT ON ${link.table}
                    FOR EACH ROW WHEN (NEW.${link.uuidCol} IS NULL OR NEW.${link.uuidCol} = '') AND NEW.${link.fkCol} IS NOT NULL
                    BEGIN
                        UPDATE ${link.table}
                           SET ${link.uuidCol} = (SELECT uuid FROM ${link.refTable} WHERE id = NEW.${link.fkCol})
                         WHERE id = NEW.id;
                    END;
                """.trimIndent())
                db.execSQL("DROP TRIGGER IF EXISTS $tUpdate")
                db.execSQL("""
                    CREATE TRIGGER $tUpdate AFTER UPDATE OF ${link.fkCol} ON ${link.table}
                    FOR EACH ROW WHEN NEW.${link.fkCol} IS NOT NULL AND IFNULL(OLD.${link.fkCol},-1) <> NEW.${link.fkCol}
                    BEGIN
                        UPDATE ${link.table}
                           SET ${link.uuidCol} = (SELECT uuid FROM ${link.refTable} WHERE id = NEW.${link.fkCol})
                         WHERE id = NEW.id;
                    END;
                """.trimIndent())
                Log.i(TAG, "   🔗 trigger ${link.table}.${link.fkCol} -> ${link.uuidCol}")
            } catch (e: Exception) { Log.w(TAG, "FK trigger hata ${link.table}: ${e.message}") }
        }

        // Yeni satirlarda uuid bos kalirsa otomatik UUID atayan trigger
        BUSINESS_TABLES.forEach { table ->
            if (!tableExists(db, table) || !columnExists(db, table, "uuid")) return@forEach
            val tg = "trg_uuid_assign_$table"
            try {
                db.execSQL("DROP TRIGGER IF EXISTS $tg")
                db.execSQL("""
                    CREATE TRIGGER $tg AFTER INSERT ON $table
                    FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
                    BEGIN
                        UPDATE $table SET uuid = lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random())%4+1,1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))
                          WHERE id = NEW.id;
                    END;
                """.trimIndent())
            } catch (e: Exception) { Log.w(TAG, "uuid auto trigger hata $table: ${e.message}") }
        }
    }

    /**
     * Her tabloya INSERT/UPDATE/DELETE change_log triggerlari kurar.
     * Sync motoru change_log uzerinden UUID bazli senkronize eder.
     */
    private fun installChangeLogTriggers(db: SQLiteDatabase) {
        if (!tableExists(db, "change_log")) {
            Log.w(TAG, "change_log tablosu yok, sync triggerleri atlandi")
            return
        }
        BUSINESS_TABLES.forEach { table ->
            if (!tableExists(db, table) || !columnExists(db, table, "uuid")) return@forEach
            val tInsert = "trg_log_ins_$table"
            val tUpdate = "trg_log_upd_$table"
            val tDelete = "trg_log_del_$table"
            try {
                db.execSQL("DROP TRIGGER IF EXISTS $tInsert")
                db.execSQL("""
                    CREATE TRIGGER $tInsert AFTER INSERT ON $table
                    BEGIN
                        INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                        VALUES ('$table', 'INSERT', NEW.id, strftime('%s','now'),
                                '{"uuid":"' || COALESCE(NEW.uuid,'') || '","table":"$table","updatedAt":' || (strftime('%s','now')*1000) || '}', 0);
                    END;
                """.trimIndent())

                db.execSQL("DROP TRIGGER IF EXISTS $tUpdate")
                db.execSQL("""
                    CREATE TRIGGER $tUpdate AFTER UPDATE ON $table
                    BEGIN
                        INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                        VALUES ('$table', 'UPDATE', NEW.id, strftime('%s','now'),
                                '{"uuid":"' || COALESCE(NEW.uuid,'') || '","table":"$table","updatedAt":' || (strftime('%s','now')*1000) || '}', 0);
                    END;
                """.trimIndent())

                db.execSQL("DROP TRIGGER IF EXISTS $tDelete")
                db.execSQL("""
                    CREATE TRIGGER $tDelete AFTER DELETE ON $table
                    BEGIN
                        INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                        VALUES ('$table', 'DELETE', OLD.id, strftime('%s','now'),
                                '{"uuid":"' || COALESCE(OLD.uuid,'') || '","table":"$table"}', 0);
                    END;
                """.trimIndent())
                Log.i(TAG, "   📝 change_log triggerler: $table (INS/UPD/DEL)")
            } catch (e: Exception) { Log.w(TAG, "change_log trigger hata $table: ${e.message}") }
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
        val ok = c.count > 0; c.close(); return ok
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        try {
            val idx = c.getColumnIndex("name"); if (idx == -1) return false
            while (c.moveToNext()) if (c.getString(idx) == column) return true
        } finally { c.close() }
        return false
    }

    /**
     * siparis tablosuna sequential siparis_no_seq kolonu ekler ve INSERT trigger ile otomatik atar.
     * Backup-safe: idempotent, sadece NULL/0 satirlara dokunur, mevcut UUID/id'leri etkilemez.
     */
    private fun installSiparisSeq(db: SQLiteDatabase) {
        if (!tableExists(db, "siparis")) return
        if (!columnExists(db, "siparis", "siparis_no_seq")) {
            try {
                db.execSQL("ALTER TABLE siparis ADD COLUMN siparis_no_seq INTEGER DEFAULT 0")
                Log.i(TAG, "   ➕ siparis.siparis_no_seq")
            } catch (e: Exception) { Log.w(TAG, "siparis_no_seq ekleme hata: ${e.message}") }
        }
        // ❄️ DONDURULMUŞ KÜRESEL SIRA: bir kez atandi mi ASLA degismez.
        // Eski kayitlari id sirasina gore 1'den numarala (sadece 0 olanlar).
        try {
            db.beginTransaction()
            try {
                var nextNo = 0L
                db.rawQuery("SELECT COALESCE(MAX(siparis_no_seq),0) FROM siparis", null).use { mx ->
                    if (mx.moveToFirst()) nextNo = mx.getLong(0)
                }
                val c = db.rawQuery(
                    "SELECT id FROM siparis WHERE (siparis_no_seq IS NULL OR siparis_no_seq = 0) ORDER BY COALESCE(createdAt,0) ASC, id ASC",
                    null)
                while (c.moveToNext()) {
                    nextNo += 1
                    db.execSQL("UPDATE siparis SET siparis_no_seq = ? WHERE id = ?",
                        arrayOf<Any>(nextNo, c.getLong(0)))
                }
                c.close()
                db.setTransactionSuccessful()
                Log.i(TAG, "   ❄️ siparis_no_seq dondurularak atandi: son no=$nextNo")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.w(TAG, "siparis_no_seq backfill hata: ${e.message}") }

        // Auto-assign trigger — KÜRESEL MAX+1, sadece YENI lokal insert icin (NEW.seq=0).
        // Sync'le gelen kayit kendi seq degerini tasiyorsa (>0) trigger fire etmez = REFLOW YOK.
        try {
            db.execSQL("DROP TRIGGER IF EXISTS trg_siparis_no_seq")
            db.execSQL("""
                CREATE TRIGGER trg_siparis_no_seq AFTER INSERT ON siparis
                FOR EACH ROW WHEN (NEW.siparis_no_seq IS NULL OR NEW.siparis_no_seq = 0)
                BEGIN
                    UPDATE siparis
                       SET siparis_no_seq = (
                           SELECT COALESCE(MAX(siparis_no_seq),0) + 1
                             FROM siparis
                            WHERE id <> NEW.id
                       )
                     WHERE id = NEW.id;
                END;
            """.trimIndent())
            Log.i(TAG, "   ❄️ trg_siparis_no_seq (kuresel MAX+1, dondurulmus)")
        } catch (e: Exception) { Log.w(TAG, "trg_siparis_no_seq hata: ${e.message}") }
    }
}
