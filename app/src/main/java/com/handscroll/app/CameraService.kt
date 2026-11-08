package com.handscroll.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : LifecycleService() {

    private lateinit var executor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val history = ArrayDeque<Pair<Long, Float>>()
    private var lastSwipeTime = 0L

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadExecutor()
        startAsForeground()
        try {
            setupLandmarker()
            startCamera()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "handscroll"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "HandScroll", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HandScroll is running")
            .setContentText("Move your hand up or down to scroll")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupLandmarker() {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { image -> analyze(image) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            image.close()
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            val buffer = image.planes[0].buffer
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            val rotation = image.imageInfo.rotationDegrees.toFloat()
            image.close()
            val matrix = Matrix().apply { postRotate(rotation) }
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val mpImage = BitmapImageBuilder(upright).build()
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            // ignore a bad frame
        }
    }

    private fun onResult(result: HandLandmarkerResult) {
        val now = SystemClock.uptimeMillis()
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            history.clear()
            return
        }
        val y = hands[0][9].y()
        history.addLast(Pair(now, y))
        while (history.isNotEmpty() && now - history.first().first > 400) {
            history.removeFirst()
        }
        if (now - lastSwipeTime < 1200 || history.size < 3) return
        val dy = y - history.first().second
        if (dy < -0.20f) {
            trigger(true)
        } else if (dy > 0.20f) {
            trigger(false)
        }
    }

    private fun trigger(up: Boolean) {
        lastSwipeTime = SystemClock.uptimeMillis()
        history.clear()
        ScrollAccessibilityService.instance?.swipe(up)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        handLandmarker?.close()
        executor.shutdown()
        super.onDestroy()
    }
}
