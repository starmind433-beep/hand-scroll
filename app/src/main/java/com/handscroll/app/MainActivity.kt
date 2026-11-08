package com.handscroll.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.CAMERA] == true) {
            startCameraService()
        } else {
            status.text = "Camera permission is needed to see your hand."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "HandScroll"
            textSize = 30f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }

        val accessibilityButton = Button(this).apply {
            text = "1. Turn on accessibility"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val startButton = Button(this).apply {
            text = "2. Start"
            setOnClickListener { requestAndStart() }
        }

        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CameraService::class.java))
                updateStatus("Stopped.")
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(accessibilityButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateStatus(null)
    }

    private fun updateStatus(message: String?) {
        val accessOn = ScrollAccessibilityService.instance != null
        val line = if (accessOn) {
            "Accessibility: ON"
        } else {
            "Accessibility: OFF (tap button 1, find HandScroll, turn it on)"
        }
        status.text = if (message != null) "$message\n$line" else line
    }

    private fun requestAndStart() {
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            startCameraService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCameraService() {
        ContextCompat.startForegroundService(
            this, Intent(this, CameraService::class.java)
        )
        updateStatus("Running. Open TikTok or Instagram and move your hand.")
    }
}
