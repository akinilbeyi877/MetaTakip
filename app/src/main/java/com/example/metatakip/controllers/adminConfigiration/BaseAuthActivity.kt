package com.example.metatakip.controllers.adminConfigiration

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.feature_data.entityModel.SessionManager

abstract class BaseAuthActivity : AppCompatActivity() {

    protected lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Oturum kontrolü yapabilirsiniz
        if (!sessionManager.isLoggedIn()) {
            // LoginActivity'ye yönlendir
            // startActivity(Intent(this, LoginActivity::class.java))
            // finish()
        }
    }

    // Diğer ortak metodlar...
}