package com.example.metatakip.controllers.HomeActive



import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

class LockableScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return super.onInterceptTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}