package com.example.metatakip.feature_backup.drive

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(private val context: Context) {

    /**
     * 🔐 Google Sign-In istemcisini yapılandırır.
     * Hibrit yedekleme sistemi için gerekli Drive izinleri burada tanımlanır.
     */
    fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(DriveScopes.DRIVE_APPDATA)
            )
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Oturum açmış mevcut hesabı döndürür.
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * 🌍 Google Drive API servisini oluşturur.
     */
    fun getDriveService(): Drive {
        val account = getLastSignedInAccount()
            ?: throw IllegalStateException(
                "Google hesabı bağlı değil. Lütfen önce oturum açın."
            )

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(
                DriveScopes.DRIVE_FILE,
                DriveScopes.DRIVE_APPDATA
            )
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("MetaTakip")
            .build()
    }

    /**
     * 🚪 Oturumu kapatır.
     */
    fun signOut(activity: Activity, onDone: (() -> Unit)? = null) {
        try {
            getSignInClient().signOut().addOnCompleteListener(activity) {
                onDone?.invoke()
            }
        } catch (e: Exception) {
            onDone?.invoke()
        }
    }

    /**
     * 🔍 Kullanıcı giriş yapmış mı kontrol eder.
     */
    fun isUserSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }

    /**
     * 👤 Giriş yapan kullanıcının e-posta adresini döndürür.
     */
    fun getSignedInUserEmail(): String? {
        return getLastSignedInAccount()?.email
    }
}
