package com.example.metatakip.controllers.poupsms

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.helpers.ContactHelper

class AskSaveContactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phone = intent.getStringExtra("phone") ?: return

        AlertDialog.Builder(this)
            .setTitle("Rehbere Ekle")
            .setMessage("Bu numarayı rehbere eklemek ister misiniz?\n$phone")
            .setPositiveButton("Evet") { _, _ ->
                ContactHelper.saveContact(this, phone, "Yeni Müşteri")
                finish()
            }
            .setNegativeButton("Hayır") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
