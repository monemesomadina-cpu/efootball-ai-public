package com.soma.efootballai.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.soma.efootballai.MainActivity
import com.soma.efootballai.R
import com.soma.efootballai.state.MatchState
import com.soma.efootballai.state.MatchState.Snapshot

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.soma.efootballai.action.START"
        const val ACTION_STOP = "com.soma.efootballai.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 4102
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var frameCount = 0
    @Volatile private var processingFrame = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture("Stopped by Android")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("Stopped")
                stopSelf()
            }
            ACTION_START -> {
                if (projection != null) return START_NOT_STICKY
                startForegroundCompat()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = getResultData(intent)
                if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
                    stopCapture("Capture permission missing")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCapture(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    private fun getResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val activeProjection = manager.getMediaProjection(resultCode, resultData)
            projection = activeProjection
            activeProjection.registerCallback(projectionCallback, Handler(mainLooper))

            val (width, height, densityDpi) = screenSize()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            val thread = HandlerThread("efootball-screen-capture")
            thread.start()
            workerThread = thread
            workerHandler = Handler(thread.looper)

            reader.setOnImageAvailableListener({ source ->
                val image = try {
                    source.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                image.use {
                    frameCount++
                    // Limit OCR work to roughly every fifth frame to reduce CPU use.
                    if (frameCount % 5 == 0 && !processingFrame) {
                        processingFrame = true
                        val bitmap = imageToBitmap(it)
                        if (bitmap != null) {
                            ScoreReader.read(bitmap) { result ->
                                bitmap.recycle()
                                processingFrame = false
                                MatchState.update(
                                    Snapshot(
                                        status = "Capturing",
                                        score = result.score,
                                        clock = result.clock,
                                        rawText = result.rawText?.take(220)
                                    )
                                )
                            }
                        } else {
                            processingFrame = false
                        }
                    }
                }
            }, workerHandler)

            virtualDisplay = activeProjection.createVirtualDisplay(
                "eFootball-AI-Capture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                workerHandler
            )
            MatchState.update(Snapshot(status = "Capturing"))
        } catch (e: Exception) {
            stopCapture("Capture error: ${e.message ?: "unknown"}")
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Triple<Int, Int, Int> {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            val density = resources.displayMetrics.densityDpi
            return Triple(bounds.width(), bounds.height(), density)
        }
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = rowStride / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            padded.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            padded.recycle()
            cropped
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("eFootball AI is capturing")
            .setContentText("Screen text analysis is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun stopCapture(reason: String) {
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        val oldProjection = projection
        projection = null
        try {
            oldProjection?.unregisterCallback(projectionCallback)
            oldProjection?.stop()
        } catch (_: Exception) {}

        try { workerThread?.quitSafely() } catch (_: Exception) {}
        workerThread = null
        workerHandler = null
        processingFrame = false
        MatchState.update(Snapshot(status = reason))
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopCapture("Stopped")
        super.onDestroy()
    }
}
