package com.example.metatakip.controllers.HomeActive



import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

class HomeRootScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    // Bu değişken true ise, bu ScrollView ASLA kaymaz.
    var isScrollLocked: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isScrollLocked) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isScrollLocked) return false
        return super.onTouchEvent(ev)
    }
}