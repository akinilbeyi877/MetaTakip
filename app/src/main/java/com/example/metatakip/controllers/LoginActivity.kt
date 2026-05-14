package com.example.metatakip.controllers

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.R
import com.example.metatakip.controllers.HomeActive.HomeActivity
import com.example.metatakip.feature_data.entityModel.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // 🟢 Eğer zaten giriş yapılmışsa direkt ana sayfaya yönlendir
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInput(username, password)) {
                performLogin(username, password)
            }
        }

        // Test butonu (isteğe bağlı)
        findViewById<Button>(R.id.btnTestLogin)?.setOnClickListener {
            testLogin()
        }
    }

    private fun validateInput(username: String, password: String): Boolean {
        if (username.isEmpty()) {
            etUsername.error = "Kullanıcı adı boş olamaz"
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "Şifre boş olamaz"
            return false
        }

        return true
    }

    private fun performLogin(username: String, password: String) {
        // 🟢 Burada gerçek veritabanı sorgusu yapılacak
        // Şimdilik test amaçlı basit kontrol

        if (username == "admin" && password == "1234") {
            // Admin girişi başarılı
            sessionManager.login(
                userId = 1L,
                username = "Admin",
                isAdmin = true
            )

            Toast.makeText(this, "Admin olarak giriş yapıldı", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()

        } else if (username == "user" && password == "1234") {
            // Normal kullanıcı girişi
            sessionManager.login(
                userId = 2L,
                username = "Normal Kullanıcı",
                isAdmin = false
            )

            Toast.makeText(this, "Kullanıcı olarak giriş yapıldı", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()

        } else {
            // 🟢 Gerçek veritabanı sorgusu yapılacak yer
            // Örnek: val user = userDao.authenticate(username, password)

            Toast.makeText(this, "Geçersiz kullanıcı adı veya şifre", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testLogin() {
        // Test için hızlı giriş
        etUsername.setText("admin")
        etPassword.setText("1234")

        Toast.makeText(this, "Test bilgileri yüklendi", Toast.LENGTH_SHORT).show()
    }
}