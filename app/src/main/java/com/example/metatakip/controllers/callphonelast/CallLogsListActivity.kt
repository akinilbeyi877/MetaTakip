package com.example.metatakip.controllers.callphonelast

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CallLogsListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var btnClearAll: android.widget.ImageButton
    private lateinit var callLogsDao: CallLogsDao
    private lateinit var menuHandler: RightClickMenuHandler
    private lateinit var adapter: CallRecordAdapter
    private var callRecords: MutableList<CallRecord> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_logs_list)

        callLogsDao = CallLogsDao(this)
        menuHandler = RightClickMenuHandler(this)
        initViews()

        // ...

        // 🔥 ADAPTER'I TEK SEFER OLUŞTUR
        adapter = CallRecordAdapter(callRecords) { callRecord ->
            menuHandler.showCallRecordMenu(
                callRecord = callRecord,
                onDataChanged = {
                    refreshData()  // SADECE VERİLERİ YENİLE
                }
            )
        }
        recyclerView.adapter = adapter

        loadCallLogs()  // İLK VERİLERİ YÜKLE
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewCallLogs)
        fabAdd = findViewById(R.id.fabAddCallLog)
        btnClearAll = findViewById(R.id.btnClearAll)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddCallLogActivity::class.java))
        }

        btnClearAll.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Çağrıları Temizle")
                .setMessage("Tüm çağrı kayıtlarını silmek istediğinize emin misiniz?")
                .setPositiveButton("Hepsini Sil") { _, _ ->
                    if (callLogsDao.deleteAllCallLogs()) {
                        refreshData()
                        Toast.makeText(this, "Temizlendi", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }

    // 🔥 VERİLERİ GÜNCELLE (Silme sonrası)
    private fun refreshData() {
        val freshList = callLogsDao.getAllCallLogs()
        callRecords.clear()
        callRecords.addAll(freshList)
        adapter.notifyDataSetChanged()

        Log.d("CallLogsListActivity", "Liste yenilendi: ${callRecords.size} kayıt")

        if (callRecords.isEmpty()) {
            Toast.makeText(this, "Henüz çağrı kaydı yok", Toast.LENGTH_LONG).show()
        }
    }

    // 🔥 VERİLERİ YÜKLE (SADECE 1 TANE!)
    private fun loadCallLogs() {
        try {
            callLogsDao = CallLogsDao(this)
            refreshData()  // Aynı refresh metodunu kullan

            Log.d("CallLogsListActivity", "${callRecords.size} çağrı kaydı yüklendi")
        } catch (e: Exception) {
            Log.e("CallLogsListActivity", "Hata", e)
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCallLogs()  // Yeniden verileri yükle
        try {
            val f = android.content.IntentFilter("com.example.metatakip.CALL_LOG_ADDED")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(callAddedReceiver, f, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(callAddedReceiver, f)
            }
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(callAddedReceiver) } catch (_: Exception) {}
    }

    private val callAddedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            android.util.Log.d("CallLogsListActivity", "🔔 CALL_LOG_ADDED yayını alındı, liste yenileniyor")
            refreshData()
        }
    }
}