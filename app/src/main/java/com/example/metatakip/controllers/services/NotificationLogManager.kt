package com.example.metatakip.controllers.services

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NotificationLogEntry(
    val orderId: Long,
    val musteriAdi: String,
    val firmaAdi: String,
    val urunTipi: String,
    val tarih: Long
)

object NotificationLogManager {

    private const val PREFS = "notification_log_prefs"
    private const val KEY   = "log_entries"
    private const val MAX   = 100

    fun add(ctx: Context, entry: NotificationLogEntry) {
        val list = getAll(ctx).toMutableList()
        list.add(0, entry)
        if (list.size > MAX) list.subList(MAX, list.size).clear()
        saveAll(ctx, list)
    }

    fun getAll(ctx: Context): List<NotificationLogEntry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                NotificationLogEntry(
                    orderId    = obj.getLong("orderId"),
                    musteriAdi = obj.getString("musteriAdi"),
                    firmaAdi   = obj.getString("firmaAdi"),
                    urunTipi   = obj.getString("urunTipi"),
                    tarih      = obj.getLong("tarih")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun removeAt(ctx: Context, index: Int) {
        val list = getAll(ctx).toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            saveAll(ctx, list)
        }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, "[]").apply()
    }

    fun count(ctx: Context): Int = getAll(ctx).size

    fun saveAll(ctx: Context, list: List<NotificationLogEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("orderId",    e.orderId)
                put("musteriAdi", e.musteriAdi)
                put("firmaAdi",   e.firmaAdi)
                put("urunTipi",   e.urunTipi)
                put("tarih",      e.tarih)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
