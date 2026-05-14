package com.example.metatakip.feature_data.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.metatakip.feature_data.common.PhoneUtils
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class GenericFormHelperImpl : IGenericFormHelper {

    private var lastToast: Toast? = null

    override fun buildVoiceIntent(languageTag: String, prompt: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    override fun extractVoiceText(data: Intent?): String {
        if (data == null) return ""
        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return matches?.firstOrNull() ?: ""
    }

    override fun getMissingPhonePermissions(activity: Activity): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyArray()

        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
        }

        // Android 11+ READ_PHONE_NUMBERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_PHONE_NUMBERS)
            }
        }

        // Rehbere ekleme yapıyorsan gerekebilir (senin akışına bağlı)
        // if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        //     permissionsNeeded.add(Manifest.permission.WRITE_CONTACTS)
        // }

        return permissionsNeeded.toTypedArray()
    }

    override fun showSingleToast(activity: Activity, message: String, long: Boolean) {
        lastToast?.cancel()
        lastToast = Toast.makeText(activity, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
        lastToast?.show()
    }

    @SuppressLint("Range")
    override fun addCustomerToAndroidContacts(
        activity: Activity,
        customerName: String,
        phoneNumber: String,
        originalName: String?,
        company: String?
    ) {
        try {
            val contactName = when {
                customerName.isNotBlank() -> customerName
                !originalName.isNullOrBlank() -> originalName
                else -> "Müşteri"
            }

            val ops = ArrayList<ContentProviderOperation>()

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        contactName
                    )
                    .build()
            )

            val local = PhoneUtils.toLocalTR(phoneNumber)
            if (local.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, local)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }

            if (!company.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .withValue(
                            ContactsContract.CommonDataKinds.Organization.TYPE,
                            ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                        )
                        .build()
                )
            }

            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Note.NOTE,
                        "MetaTakip'ten eklendi - $dateString"
                    )
                    .build()
            )

            activity.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(activity, "✅ Müşteri rehbere eklendi: $contactName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("CONTACTS", "Rehber ekleme hatası", e)
            Toast.makeText(activity, "⚠️ Rehbere eklenemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}