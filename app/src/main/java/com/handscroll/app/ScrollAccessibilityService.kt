package com.handscroll.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class ScrollAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScrollAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun swipe(up: Boolean) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val h = metrics.heightPixels.toFloat()
        val startY = if (up) h * 0.75f else h * 0.25f
        val endY = if (up) h * 0.25f else h * 0.75f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
