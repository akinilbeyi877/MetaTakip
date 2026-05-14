package com.example.metatakip.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract

object ContactHelper {

    fun saveContact(context: Context, phone: String, name: String) {

        val resolver = context.contentResolver

        val rawUri = resolver.insert(
            ContactsContract.RawContacts.CONTENT_URI,
            ContentValues()
        ) ?: return

        val rawId = rawUri.lastPathSegment ?: return

        ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            put(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            resolver.insert(ContactsContract.Data.CONTENT_URI, this)
        }

        ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            put(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            resolver.insert(ContactsContract.Data.CONTENT_URI, this)
        }
    }
}
