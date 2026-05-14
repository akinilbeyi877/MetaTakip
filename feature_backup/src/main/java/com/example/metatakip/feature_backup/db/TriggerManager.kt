package com.example.metatakip.feature_backup.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * 🚦 Hibrit Senkronizasyon Tetikleyici Yöneticisi (DÜZELTİLMİŞ)
 * Veri silinmesini ve çiftlemeyi önlemek için optimize edilmiştir.
 */
object TriggerManager {
    private const val TAG = "TriggerManager"

    fun createAllTriggers(db: SQLiteDatabase) {
        try {
            dropAllTriggers(db)

            // 🌍 ANA TABLOLAR
            createMusteriTriggers(db)
            createSiparisTriggers(db)
            createUrunTriggers(db)
            createFirmaTriggers(db)
            createPersonelTriggers(db)
            createUrunTipiTriggers(db)

            // 🛠️ YARDIMCI TABLOLAR
            createMesajSablonTriggers(db)
            createUnvanTriggers(db)
            createEtiketSablonTriggers(db)
            createEtiketSablonBilesenTriggers(db)
            createEtiketSayfaAyarTriggers(db)
            createCallLogsTriggers(db)
            createDeleteLogTriggers(db)

            Log.d(TAG, "✅ Tüm Hibrit Trigger'lar ve Güvenli UUID mekanizması başarıyla kuruldu.")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Trigger oluşturma hatası: ${e.message}")
        }
    }

    private fun getUuidGeneratorSql(): String {
        return "(lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))))"
    }

    private fun createMusteriTriggers(db: SQLiteDatabase) {
        // 1. UUID ATAMA: Sadece boşsa atar. (Dışarıdan gelen veriye dokunmaz)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_musteri_uuid_assign AFTER INSERT ON musteri
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                UPDATE musteri SET uuid = ${getUuidGeneratorSql()} WHERE id = NEW.id;
            END;
        """.trimIndent())

        // 2. LOGLAMA: Çiftlemeyi önlemek için 'INSERT' anında UUID zaten varsa (dışarıdan geliyorsa) log üretmez.
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_musteri_insert
            AFTER INSERT ON musteri
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES (
                    'musteri', 'INSERT', NEW.id, strftime('%s','now'),
                    '{' || 
                    '"uuid":"' || COALESCE(NEW.uuid, '') || '",' ||
                    '"adSoyad":"' || COALESCE(REPLACE(NEW.adSoyad, '"', '\"'), '') || '",' ||
                    '"table":"musteri",' ||
                    '"updatedAt":' || (strftime('%s','now') * 1000) ||
                    '}', 0
                );
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_musteri_update
            AFTER UPDATE ON musteri
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES (
                    'musteri', 'UPDATE', NEW.id, strftime('%s','now'),
                    '{' || 
                    '"uuid":"' || COALESCE(NEW.uuid, '') || '",' ||
                    '"adSoyad":"' || COALESCE(REPLACE(NEW.adSoyad, '"', '\"'), '') || '",' ||
                    '"table":"musteri",' ||
                    '"updatedAt":' || (strftime('%s','now') * 1000) ||
                    '}', 0
                );
            END;
        """.trimIndent())
    }

    private fun createSiparisTriggers(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_siparis_uuid_assign AFTER INSERT ON siparis
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                UPDATE siparis SET uuid = ${getUuidGeneratorSql()} WHERE id = NEW.id;
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_siparis_insert
            AFTER INSERT ON siparis
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES (
                    'siparis', 'INSERT', NEW.id, strftime('%s','now'),
                    '{' || 
                    '"uuid":"' || COALESCE(NEW.uuid, '') || '",' ||
                    '"musteri_uuid":"' || COALESCE(NEW.musteri_uuid, '') || '",' ||
                    '"table":"siparis",' ||
                    '"updatedAt":' || (strftime('%s','now') * 1000) ||
                    '}', 0
                );
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_siparis_update
            AFTER UPDATE ON siparis
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES (
                    'siparis', 'UPDATE', NEW.id, strftime('%s','now'),
                    '{"uuid":"'||COALESCE(NEW.uuid,'')||'","durum":"'||COALESCE(NEW.durum,'')||'","table":"siparis","updatedAt":'||(strftime('%s','now')*1000)||'}', 0
                );
            END;
        """.trimIndent())
    }

    private fun createUrunTriggers(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_urun_uuid_assign AFTER INSERT ON urun
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                UPDATE urun SET uuid = ${getUuidGeneratorSql()} WHERE id = NEW.id;
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_urun_insert AFTER INSERT ON urun
            FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES ('urun', 'INSERT', NEW.id, strftime('%s','now'), 
                '{"uuid":"'||COALESCE(NEW.uuid,'')||'","siparis_uuid":"'||COALESCE(NEW.siparis_uuid,'')||'","ad":"'||COALESCE(REPLACE(NEW.ad, '"', '\"'), '')||'","table":"urun","updatedAt":'||(strftime('%s','now')*1000)||'}', 0);
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_urun_update AFTER UPDATE ON urun
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES ('urun', 'UPDATE', NEW.id, strftime('%s','now'), 
                '{"uuid":"'||COALESCE(NEW.uuid,'')||'","tutar":'||COALESCE(NEW.tutar,0)||',"table":"urun","updatedAt":'||(strftime('%s','now')*1000)||'}', 0);
            END;
        """.trimIndent())
    }

    private fun createFirmaTriggers(db: SQLiteDatabase) {
        if (columnExists(db, "firma", "uuid")) {
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trigger_firma_uuid_assign AFTER INSERT ON firma
                FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
                BEGIN
                    UPDATE firma SET uuid = ${getUuidGeneratorSql()} WHERE id = NEW.id;
                END;
            """.trimIndent())
        }

        val actions = listOf("INSERT", "UPDATE")
        actions.forEach { action ->
            val uuidValue = if (columnExists(db, "firma", "uuid")) "COALESCE(NEW.uuid, '')" else "''"
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trigger_firma_${action.lowercase()}
                AFTER $action ON firma
                BEGIN
                    INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                    VALUES ('firma', '$action', NEW.id, strftime('%s','now'), 
                    '{"uuid":"'||$uuidValue||'","firmaAdi":"'||COALESCE(REPLACE(NEW.firmaAdi, '"', '\"'), '')||'","table":"firma","updatedAt":'||(strftime('%s','now')*1000)||'}', 0);
                END;
            """.trimIndent())
        }
    }

    private fun createUrunTipiTriggers(db: SQLiteDatabase) {
        val actions = listOf("INSERT", "UPDATE")
        actions.forEach { action ->
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trigger_urun_tipi_${action.lowercase()}
                AFTER $action ON urun_tipi
                BEGIN
                    INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                    VALUES ('urun_tipi', '$action', NEW.id, strftime('%s','now'), 
                    '{"uuid":"'||COALESCE(NEW.uuid,'')||'","ad":"'||COALESCE(REPLACE(NEW.ad, '"', '\"'), '')||'","table":"urun_tipi","updatedAt":'||(strftime('%s','now')*1000)||'}', 0);
                END;
            """.trimIndent())
        }
    }

    private fun createPersonelTriggers(db: SQLiteDatabase) {
        val actions = listOf("INSERT", "UPDATE")
        actions.forEach { action ->
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trigger_personel_${action.lowercase()} 
                AFTER $action ON personel 
                BEGIN 
                    INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) 
                    VALUES ('personel', '$action', NEW.id, strftime('%s','now'), 
                    '{"uuid":"'||COALESCE(NEW.uuid,'')||'","adSoyad":"'||COALESCE(REPLACE(NEW.adSoyad, '"', '\"'),'')||'","table":"personel","updatedAt":'||(strftime('%s','now')*1000)||'}', 0); 
                END;
            """.trimIndent())
        }
    }

    private fun createDeleteLogTriggers(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_delete_log_insert AFTER INSERT ON delete_log
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES ('delete_log', 'INSERT', NEW.id, strftime('%s','now'), 
                '{"id":'||NEW.id||',"type":"'||COALESCE(NEW.entityType,'')||'","recordId":'||NEW.entityId||'}', 0);
            END;
        """.trimIndent())
    }

    private fun createCallLogsTriggers(db: SQLiteDatabase) {
        if (columnExists(db, "call_logs", "uuid")) {
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trigger_call_logs_uuid_assign AFTER INSERT ON call_logs
                FOR EACH ROW WHEN (NEW.uuid IS NULL OR NEW.uuid = '')
                BEGIN
                    UPDATE call_logs SET uuid = ${getUuidGeneratorSql()} WHERE id = NEW.id;
                END;
            """.trimIndent())
        }
        val uuidExpr = if (columnExists(db, "call_logs", "uuid")) "COALESCE(NEW.uuid, '')" else "''"
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trigger_call_logs_insert AFTER INSERT ON call_logs
            BEGIN
                INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced)
                VALUES ('call_logs', 'INSERT', NEW.id, strftime('%s','now'),
                '{"uuid":"'||$uuidExpr||'","tel":"'||COALESCE(NEW.musteriTelefonu,'')||'","tur":"'||COALESCE(NEW.cagriTuru,'')||'","table":"call_logs","updatedAt":'||(strftime('%s','now')*1000)||'}', 0);
            END;
        """.trimIndent())
    }

    private fun createUnvanTriggers(db: SQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_unvan_insert AFTER INSERT ON unvan BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('unvan', 'INSERT', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"ad\":\"'||COALESCE(NEW.ad,'')||'\"}', 0); END;")
    }

    private fun createMesajSablonTriggers(db: SQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_mesaj_sablon_insert AFTER INSERT ON mesaj_sablon BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('mesaj_sablon', 'INSERT', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"baslik\":\"'||COALESCE(NEW.baslik,'')||'\"}', 0); END;")
    }

    private fun createEtiketSablonTriggers(db: SQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_etiket_sablon_insert AFTER INSERT ON etiket_sablon BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('etiket_sablon', 'INSERT', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"adi\":\"'||COALESCE(NEW.adi,'')||'\"}', 0); END;")
        // v351: UPDATE trigger — yazdirma metni (manual_text/comp_text) degisiklikleri buluta gitsin
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_etiket_sablon_update AFTER UPDATE ON etiket_sablon BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('etiket_sablon', 'UPDATE', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"adi\":\"'||COALESCE(NEW.adi,'')||'\",\"table\":\"etiket_sablon\",\"updatedAt\":'||(strftime('%s','now')*1000)||'}', 0); END;")
    }

    private fun createEtiketSablonBilesenTriggers(db: SQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_etiket_sablon_bilesen_insert AFTER INSERT ON etiket_sablon_bilesen BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('etiket_sablon_bilesen', 'INSERT', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"sablon_id\":'||NEW.sablon_id||'}', 0); END;")
    }

    private fun createEtiketSayfaAyarTriggers(db: SQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trigger_etiket_sayfa_ayar_insert AFTER INSERT ON etiket_sayfa_ayar BEGIN INSERT INTO change_log (table_name, action_type, record_id, changed_at, details, synced) VALUES ('etiket_sayfa_ayar', 'INSERT', NEW.id, strftime('%s','now'), '{\"id\":'||NEW.id||',\"sablon_id\":'||NEW.sablon_id||'}', 0); END;")
    }

    private fun columnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        try {
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) return true
            }
        } finally { cursor.close() }
        return false
    }

    fun dropAllTriggers(db: SQLiteDatabase) {
        val tables = listOf("musteri", "siparis", "firma", "urun", "urun_tipi", "personel", "mesaj_sablon", "unvan", "etiket_sablon", "etiket_sablon_bilesen", "etiket_sayfa_ayar", "call_logs", "delete_log")
        tables.forEach { table ->
            db.execSQL("DROP TRIGGER IF EXISTS trigger_${table}_uuid_assign")
            db.execSQL("DROP TRIGGER IF EXISTS trigger_${table}_insert")
            db.execSQL("DROP TRIGGER IF EXISTS trigger_${table}_update")
            db.execSQL("DROP TRIGGER IF EXISTS trigger_${table}_delete")
        }
    }
}