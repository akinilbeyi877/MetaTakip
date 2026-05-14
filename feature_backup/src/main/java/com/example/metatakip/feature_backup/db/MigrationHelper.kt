package com.example.metatakip.feature_backup.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.util.DbFilePaths
import com.example.metatakip.feature_backup.util.MetaTakipDbLock
import com.example.metatakip.feature_data.db.MetaTakipDb

object MigrationHelper {

    private const val TAG = "MigrationHelper"

    /**
     * Uygulama açılışında çağrılır. Senkronizasyon altyapısını kurar.
     * Artık global kilit ve MetaTakipDb havuzu kullanılır.
     */
    fun checkAndMigrate(context: Context) {
        synchronized(MetaTakipDbLock.lock) {
            val db = MetaTakipDb.getInstance(context).writableDatabase
            try {
                ensureRequiredColumns(db)
                if (!isTableExists(db, "change_log")) {
                    createChangeLogTable(db)
                } else {
                    recreateChangeLogTableIfSchemaBroken(db)
                    upgradeChangeLogTableIfNeeded(db)
                }
                repairMissingUuids(db)
                safeCreateTriggers(db)
                Log.i(TAG, "✅ MigrationHelper: Senkronizasyon altyapısı ve şema onarımı hazır.")
            } catch (e: Exception) {
                Log.e(TAG, "💥 Migration hatası: ${e.message}", e)
            }
            // ASLA db.close() yapma – bağlantı havuzda kalır
        }
    }

    // ---------- YARDIMCI FONKSİYONLAR (db parametresi dışarıdan gelir) ----------

    private fun ensureRequiredColumns(db: SQLiteDatabase) {
        val tablesToFix = listOf("musteri", "siparis", "urun", "firma", "personel", "urun_tipi", "call_logs")
        for (table in tablesToFix) {
            if (isTableExists(db, table)) {
                if (!isColumnExists(db, table, "uuid")) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT")
                        Log.d(TAG, "➕ $table tablosuna 'uuid' kolonu eklendi.")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ $table tablosuna uuid eklenemedi: ${e.message}")
                    }
                }
                if (!isColumnExists(db, table, "updatedAt")) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER DEFAULT 0")
                        Log.d(TAG, "➕ $table tablosuna 'updatedAt' kolonu eklendi.")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ $table tablosuna updatedAt eklenemedi: ${e.message}")
                    }
                }
            }
        }
    }

    private fun createChangeLogTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS change_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                action_type TEXT NOT NULL,
                record_id INTEGER,
                changed_at INTEGER NOT NULL,
                user_id INTEGER DEFAULT 0,
                details TEXT, 
                synced INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_change_log_changed_at ON change_log(changed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_change_log_synced ON change_log(synced)")
        Log.d(TAG, "✅ change_log tablosu ve indeksleri oluşturuldu.")
    }

    private fun repairMissingUuids(db: SQLiteDatabase) {
        val tablesToRepair = listOf("musteri", "siparis", "urun", "firma", "personel", "urun_tipi", "call_logs")
        db.beginTransaction()
        try {
            for (table in tablesToRepair) {
                if (isTableExists(db, table)) {
                    val updateSql = """
                        UPDATE $table SET uuid = (
                            lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || 
                            substr(hex(randomblob(2)), 2) || '-' || 
                            substr('89ab', 1 + (abs(random()) % 4), 1) || 
                            substr(hex(randomblob(2)), 2) || '-' || 
                            hex(randomblob(6)))
                        ) WHERE uuid IS NULL OR uuid = '';
                    """.trimIndent()
                    db.execSQL(updateSql)
                }
            }
            db.setTransactionSuccessful()
            Log.d(TAG, "✅ Mevcut NULL UUID'ler onarıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ UUID Onarımı hatası: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    private fun recreateChangeLogTableIfSchemaBroken(db: SQLiteDatabase) {
        val requiredColumns = listOf("id", "table_name", "action_type", "record_id", "changed_at", "user_id", "details", "synced")
        val existingColumns = getColumns(db, "change_log")
        val missing = requiredColumns.filter { it !in existingColumns }
        if (missing.isNotEmpty()) {
            Log.w(TAG, "⚠️ change_log şeması hatalı. Yeniden oluşturuluyor...")
            db.execSQL("DROP TABLE IF EXISTS change_log")
            createChangeLogTable(db)
        }
    }

    private fun upgradeChangeLogTableIfNeeded(db: SQLiteDatabase) {
        if (!isColumnExists(db, "change_log", "user_id")) {
            db.execSQL("ALTER TABLE change_log ADD COLUMN user_id INTEGER DEFAULT 0")
        }
        if (!isColumnExists(db, "change_log", "details")) {
            db.execSQL("ALTER TABLE change_log ADD COLUMN details TEXT")
        }
        if (!isColumnExists(db, "change_log", "synced")) {
            db.execSQL("ALTER TABLE change_log ADD COLUMN synced INTEGER DEFAULT 0")
        }
    }

    private fun safeCreateTriggers(db: SQLiteDatabase) {
        val requiredTables = listOf(
            "musteri", "siparis", "firma", "urun", "urun_tipi",
            "personel", "mesaj_sablon", "unvan", "etiket_sablon",
            "etiket_sablon_bilesen", "etiket_sayfa_ayar", "call_logs", "delete_log"
        )
        val missingTables = requiredTables.filterNot { isTableExists(db, it) }
        if (missingTables.isNotEmpty()) return
        try {
            TriggerManager.createAllTriggers(db)
            Log.d(TAG, "✅ Trigger'lar güncellendi.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Trigger hatası: ${e.message}")
        }
    }

    private fun isTableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        return cursor.use { it.moveToFirst() }
    }

    private fun isColumnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        cursor.use {
            while (it.moveToNext()) {
                if (it.getString(1) == columnName) return true
            }
        }
        return false
    }

    private fun getColumns(db: SQLiteDatabase, tableName: String): List<String> {
        val result = mutableListOf<String>()
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        cursor.use {
            while (it.moveToNext()) { result.add(it.getString(1)) }
        }
        return result
    }
}