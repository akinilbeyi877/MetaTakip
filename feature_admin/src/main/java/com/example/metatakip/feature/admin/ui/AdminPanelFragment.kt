package com.example.metatakip.feature.admin.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.metatakip.feature.admin.R

class AdminPanelFragment : Fragment(R.layout.fragment_admin_panel) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 💬 Mesaj Şablonları butonu
        view.findViewById<Button>(R.id.btnMesajSablonlari).setOnClickListener {

            val intent = Intent("com.example.metatakip.OPEN_GENERIC_LIST").apply {
                putExtra("listType", "mesaj_sablon")
                putExtra("pageTitle", "💬 Mesaj Şablonları")
                putExtra("source", "admin")
            }

            startActivity(intent)
        }
    }
}
