package com.example.metatakip.utils

import android.content.Context

fun Int.dp(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
