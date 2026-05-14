package com.example.metatakip.feature_data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.metatakip.feature_data.db.feature_admin.FeatureAdminDbTables
import com.example.metatakip.feature_data.db.feature_uruntipi.FeatureUrunTipiDbTables
import com.example.metatakip.feature_data.db.feature_user.FeatureUserDbTables
import com.example.metatakip.feature_data.db.feature_musteri.FeatureMusteriDbTables
import com.example.metatakip.feature_data.db.feature_siparis.FeatureSiparisDbTables
import com.example.metatakip.feature_data.db.feature_urun.FeatureUrunDbTables
import com.example.metatakip.feature_data.db.feature_firma.FeatureFirmaDbTables
import com.example.metatakip.feature_data.db.feature_deletelog.FeatureDeleteLogDbTables
import com.example.metatakip.feature_data.db.feature_calllogs.FeatureCallLogsDbTables
import com.example.metatakip.feature_data.db.feature_etiket_sablon.FeatureEtiketSablonDbTables
import com.example.metatakip.feature_data.db.feature_etiket_sablon_bilesen.FeatureEtiketSablonBilesenDbTables
import com.example.metatakip.feature_data.db.feature_etiket_sayfa_ayar.FeatureEtiketSayfaAyarDbTables
import com.example.metatakip.feature_data.db.feature_unvan.FeatureUnvanDbTables

class MetaTakipDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "MetaTakip.db"
        private const val DB_VERSION = 410
        @Volatile var appContext: android.content.Context? = null
        private const val TAG = "MetaTakipDb"

        @Volatile
        private var INSTANCE: MetaTakipDb? = null

        fun getInstance(context: Context): MetaTakipDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetaTakipDb(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            Log.d(TAG, "🚀 onCreate() Hibrit v$DB_VERSION başlatılıyor...")
            createAllTables(db)
            createIndexes(db)
            createDefaultAdminUser(db)
            UniversalSchemaMigration.migrate(db)
            Log.i(TAG, "✅ DB HİBRİT OLARAK OLUŞTURULDU (v$DB_VERSION)")
            checkCriticalTablesAndColumns(db, DB_VERSION)
        } catch (e: Exception) {
            Log.e(TAG, "💥 onCreate BAŞARISIZ", e)
            throw e
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "🔄 UPGRADE BAŞLADI: $oldVersion → $newVersion")
        try {
            if (oldVersion < 100) {
                Log.w(TAG, "⚠️ Çok eski versiyon ($oldVersion), full reset yapılıyor")
                dropAllTables(db)
                onCreate(db)
                return
            }
            FeatureUserDbTables.upgrade(db, oldVersion, newVersion)
            FeatureFirmaDbTables.upgrade(db, oldVersion, newVersion)
            FeatureMusteriDbTables.upgrade(db, oldVersion, newVersion)
            FeatureSiparisDbTables.upgrade(db, oldVersion, newVersion)
            FeatureUrunDbTables.upgrade(db, oldVersion, newVersion)
            FeatureDeleteLogDbTables.upgrade(db, oldVersion, newVersion)
            FeatureCallLogsDbTables.upgrade(db, oldVersion, newVersion)
            FeatureEtiketSablonDbTables.upgrade(db, oldVersion, newVersion)
            FeatureEtiketSablonBilesenDbTables.upgrade(db, oldVersion, newVersion)
            FeatureEtiketSayfaAyarDbTables.upgrade(db, oldVersion, newVersion)
            FeatureUnvanDbTables.upgrade(db, oldVersion, newVersion)
            FeaturePersonelDbTables.upgrade(db, oldVersion, newVersion)
            FeatureUrunTipiDbTables.upgrade(db, oldVersion, newVersion)
            FeatureAdminDbTables.upgrade(db, oldVersion, newVersion)
            UniversalSchemaMigration.migrate(db)
            Log.i(TAG, "✅ DB UPGRADE TAMAMLANDI")
            checkCriticalTablesAndColumns(db, oldVersion)
            checkCriticalHybridColumns(db)
        } catch (e: Exception) {
            Log.e(TAG, "💥 Upgrade BAŞARISIZ -> RESET", e)
            dropAllTables(db)
            onCreate(db)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        Log.d(TAG, "🔧 Foreign key constraints ENABLED")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.rawQuery("PRAGMA journal_mode=WAL", null).close()
            db.rawQuery("PRAGMA busy_timeout=3000", null).close()
            Log.d(TAG, "✅ WAL mode active, busy_timeout=3000ms")
        } catch (e: Exception) {
            Log.e(TAG, "PRAGMA ayarları yapılamadı: ${e.message}")
        }
        cleanupBrokenTriggers(db)
        ensureCallLogsUuidColumn(db)
    }

    private fun cleanupBrokenTriggers(db: SQLiteDatabase) {
        try {
            val cur = db.rawQuery(
                "SELECT name, sql FROM sqlite_master WHERE type='trigger' AND sql IS NOT NULL",
                null
            )
            val toDrop = mutableListOf<String>()
            cur.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    val sql = it.getString(1) ?: ""
                    if (sql.contains("NEW.musteriId") ||
                        sql.contains("NEW.frozen_seq") ||
                        sql.contains("frozen_seq")) {
                        toDrop += name
                    }
                }
            }
            for (n in toDrop) {
                try {
                    db.execSQL("DROP TRIGGER IF EXISTS $n")
                    Log.w(TAG, "🧹 Bozuk trigger silindi: $n")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Trigger silinemedi $n: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanupBrokenTriggers hatası: ${e.message}")
        }
    }

    private fun ensureCallLogsUuidColumn(db: SQLiteDatabase) {
        try {
            if (!tableExists(db, "call_logs")) return
            if (!columnExists(db, "call_logs", "uuid")) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN uuid TEXT")
                Log.d(TAG, "➕ call_logs.uuid kolonu eklendi")
            }
            if (!columnExists(db, "call_logs", "updatedAt")) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN updatedAt INTEGER DEFAULT 0")
                Log.d(TAG, "➕ call_logs.updatedAt kolonu eklendi")
            }
            db.execSQL("""
                UPDATE call_logs SET uuid = (
                    lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                    substr(hex(randomblob(2)), 2) || '-' ||
                    substr('89ab', 1 + (abs(random()) % 4), 1) ||
                    substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))
                ) WHERE uuid IS NULL OR uuid = ''
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "ensureCallLogsUuidColumn hatası: ${e.message}")
        }
    }

    private fun createAllTables(db: SQLiteDatabase) {
        FeatureUserDbTables.create(db)
        FeatureFirmaDbTables.create(db)
        FeatureMusteriDbTables.create(db)
        FeatureSiparisDbTables.create(db)
        FeatureUrunDbTables.create(db)
        FeatureDeleteLogDbTables.create(db)
        FeatureCallLogsDbTables.create(db)
        FeatureEtiketSablonDbTables.create(db)
        FeatureEtiketSablonBilesenDbTables.create(db)
        FeatureEtiketSayfaAyarDbTables.create(db)
        FeatureUnvanDbTables.create(db)
        FeaturePersonelDbTables.create(db)
        FeatureAdminDbTables.create(db)
        FeatureUrunTipiDbTables.create(db)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_musteri_ceptel ON musteri(ceptel)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_logs_tel ON call_logs(musteriTelefonu)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_logs_time ON call_logs(cagriZamani)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mesaj_sablon_firmaid ON mesaj_sablon(firmaid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mesaj_sablon_tip ON mesaj_sablon(tip)")
    }

    private fun createDefaultAdminUser(db: SQLiteDatabase) {
        try {
            val c = db.rawQuery("SELECT COUNT(*) FROM user", null)
            val count = if (c.moveToFirst()) c.getInt(0) else 0
            c.close()
            if (count == 0) {
                val v = ContentValues().apply {
                    put("username", "admin")
                    put("password", "1234")
                    put("fullName", "Admin")
                    put("role", "ADMIN")
                }
                db.insert("user", null, v)
                Log.d(TAG, "👤 Varsayılan admin kullanıcı oluşturuldu")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Admin kullanıcı oluşturulamadı", e)
        }
    }

    private fun dropAllTables(db: SQLiteDatabase) {
        val tables = listOf(
            "call_logs", "etiket_sayfa_ayar", "etiket_sablon_bilesen",
            "etiket_sablon", "personel", "unvan", "urun", "urun_tipi",
            "siparis", "musteri", "firma", "user", "delete_log", "mesaj_sablon"
        )
        tables.forEach { table ->
            try { db.execSQL("DROP TABLE IF EXISTS $table") } catch (e: Exception) { Log.e(TAG, "❌ $table silinemedi: ${e.message}") }
        }
    }

    private fun checkCriticalHybridColumns(db: SQLiteDatabase) {
        val tables = listOf("musteri", "siparis", "urun")
        tables.forEach { table ->
            if (!columnExists(db, table, "isDeleted")) {
                if (columnExists(db, table, "is_deleted")) db.execSQL("ALTER TABLE $table RENAME COLUMN is_deleted TO isDeleted")
                else db.execSQL("ALTER TABLE $table ADD COLUMN isDeleted INTEGER DEFAULT 0")
            }
            if (!columnExists(db, table, "updatedAt")) {
                if (columnExists(db, table, "updated_at")) db.execSQL("ALTER TABLE $table RENAME COLUMN updated_at TO updatedAt")
                else db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER DEFAULT (strftime('%s','now'))")
            }
        }
    }

    private fun checkCriticalTablesAndColumns(db: SQLiteDatabase, currentVersion: Int) {
        Log.w(TAG, "🔍 KRİTİK TABLO KONTROLÜ BAŞLIYOR (v$currentVersion)")
        val criticalTables = listOf("urun_tipi", "mesaj_sablon", "user", "siparis", "musteri", "urun")
        criticalTables.forEach { checkTableAndColumns(db, it, currentVersion) }
    }

    private fun checkTableAndColumns(db: SQLiteDatabase, tableName: String, currentVersion: Int) {
        try {
            if (!tableExists(db, tableName)) {
                Log.e(TAG, "🚨 Tablo $tableName YOK! Oluşturuluyor...")
                when (tableName) {
                    "urun_tipi" -> FeatureUrunTipiDbTables.create(db)
                    "mesaj_sablon" -> FeatureAdminDbTables.create(db)
                    "musteri" -> FeatureMusteriDbTables.create(db)
                    "siparis" -> FeatureSiparisDbTables.create(db)
                    "urun" -> FeatureUrunDbTables.create(db)
                    "user" -> FeatureUserDbTables.create(db)
                }
                return
            }
            if (tableName == "urun_tipi") {
                if (!columnExists(db, tableName, "hesap_tipi")) db.execSQL("ALTER TABLE $tableName ADD COLUMN hesap_tipi TEXT DEFAULT 'M2'")
                if (!columnExists(db, tableName, "aktif")) db.execSQL("ALTER TABLE $tableName ADD COLUMN aktif INTEGER DEFAULT 1")
            }
            if (tableName == "mesaj_sablon") {
                if (!columnExists(db, tableName, "tip")) db.execSQL("ALTER TABLE $tableName ADD COLUMN tip TEXT DEFAULT 'genel'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Tablo kontrol hatası ($tableName): ${e.message}")
        }
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        val nameIdx = c.getColumnIndex("name")
        if (nameIdx != -1) {
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == column) { exists = true; break }
            }
        }
        c.close()
        return exists
    }

    fun manualCheckAndFix(db: SQLiteDatabase) {
        checkCriticalTablesAndColumns(db, DB_VERSION)
        checkCriticalHybridColumns(db)
    }
}