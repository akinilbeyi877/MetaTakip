package com.example.metatakip.feature_backup_drive.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.metatakip.feature_backup_drive.data.ImportRowResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object JsonTableImportManager {

    private const val DB_NAME = "MetaTakip.db"

    fun previewImport(file: File): Pair<String, List<ImportRowResult>> {
        val text = file.readText()
        val obj = JSONObject(text)

        val table = obj.getString("table")
        val rows = obj.getJSONArray("rows")

        val results = mutableListOf<ImportRowResult>()

        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val result = validateRow(table, row, i + 1)
            results.add(result)
        }

        return table to results
    }

    fun importAppend(context: Context, file: File): List<ImportRowResult> {
        val dbFile = context.getDatabasePath(DB_NAME)
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

        val text = file.readText()
        val obj = JSONObject(text)

        val table = obj.getString("table")
        val rows = obj.getJSONArray("rows")

        val results = mutableListOf<ImportRowResult>()

        db.beginTransaction()
        try {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val check = validateRow(table, row, i + 1)

                if (check.status == ImportRowResult.Status.ERROR) {
                    results.add(check)
                    continue
                }

                try {
                    insertRow(db, table, row)
                    results.add(
                        ImportRowResult(
                            rowIndex = i + 1,
                            status = ImportRowResult.Status.OK,
                            message = "Eklendi"
                        )
                    )
                } catch (e: Exception) {
                    results.add(
                        ImportRowResult(
                            rowIndex = i + 1,
                            status = ImportRowResult.Status.ERROR,
                            message = "DB hatası: ${e.message}"
                        )
                    )
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }

        return results
    }

    private fun validateRow(table: String, row: JSONObject, rowIndex: Int): ImportRowResult {
        return when (table) {
            "firma" -> {
                val firmaAdi = row.optString("firmaAdi")
                if (firmaAdi.isBlank()) {
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "firmaAdi boş olamaz")
                } else {
                    ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
                }
            }

            "musteri" -> {
                val adSoyad = row.optString("adSoyad")
                if (adSoyad.isBlank()) {
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "adSoyad boş olamaz")
                } else {
                    ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
                }
            }

            "urun_tipi" -> {
                val ad = row.optString("ad")
                if (ad.isBlank()) {
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "ad boş olamaz")
                } else {
                    ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
                }
            }

            "mesaj_sablon" -> {
                val baslik = row.optString("baslik")
                if (baslik.isBlank()) {
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "baslik boş olamaz")
                } else {
                    ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
                }
            }

            "unvan" -> {
                val ad = row.optString("ad")
                if (ad.isBlank()) {
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "ad boş olamaz")
                } else {
                    ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
                }
            }

            else -> {
                ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Desteklenmeyen tablo: $table")
            }
        }
    }

    private fun insertRow(db: SQLiteDatabase, table: String, row: JSONObject) {
        val ignoredColumns = setOf(
            "id", "createdAt", "created_at", "updated_at",
            "deletedAt", "isDeleted", "is_deleted"
        )

        val keys = row.keys().asSequence()
            .filterNot { it in ignoredColumns }
            .toList()

        if (keys.isEmpty()) return

        val cols = keys.joinToString(",")
        val qs = keys.joinToString(",") { "?" }

        val sql = "INSERT INTO $table ($cols) VALUES ($qs)"

        val args = keys.map { key ->
            val value = row.opt(key)
            when {
                value == null || value == JSONObject.NULL -> null
                value is Boolean -> if (value) 1 else 0
                else -> value
            }
        }.toTypedArray()

        db.execSQL(sql, args)
    }
}