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
import java.util.Collections

class DriveAuthManager(private val context: Context) {

    /**
     * 🔐 Google Sign-In istemcisini yapılandırır.
     * DRIVE_FILE scope'u, uygulamanın sadece kendi oluşturduğu dosyalara erişmesini sağlar.
     */
    fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // Hibrit yedekleme için sadece uygulama dosyalarına erişim (DRIVE_FILE) yeterli ve en güvenlisidir.
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Oturum açmış olan mevcut hesabı döndürür.
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * 🌍 Drive API servisini oluşturur.
     * Senkronizasyon paketlerini (ZIP) Drive'a yüklemek için bu servis kullanılır.
     */
    fun getDriveService(): Drive {
        val account = getLastSignedInAccount()
            ?: throw IllegalStateException("Google hesabı bağlı değil. Lütfen önce oturum açın.")

        // Google hesabı üzerinden yetkilendirme (Credential) oluşturma
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("MetaTakip") // Drive işlemlerinde uygulamanın adını belirtir
            .build()
    }

    /**
     * 🚪 Oturumu kapatır.
     * Farklı bir cihazda farklı bir kullanıcıyla test yaparken hayati önem taşır.
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
     * 🔍 Ekstra Kontrol: Hesabın hala geçerli olup olmadığını doğrular.
     */
    fun isUserSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }

    /**
     * 👤 Kullanıcı E-posta Bilgisini Al:
     * Senkronizasyon loglarında "Hangi hesapla yedek alındı?" bilgisini göstermek için kullanılır.
     */
    fun getSignedInUserEmail(): String? {
        return getLastSignedInAccount()?.email
    }
}