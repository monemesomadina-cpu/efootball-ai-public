package com.soma.efootballai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.soma.efootballai.capture.ScreenCaptureService
import com.soma.efootballai.state.MatchState

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var clockText: TextView
    private lateinit var ocrText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                statusText.text = "Status: Starting capture…"
                startButton.isEnabled = false
                stopButton.isEnabled = true
            } else {
                statusText.text = "Status: Screen capture permission was not granted"
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    private val stateListener: (MatchState.Snapshot) -> Unit = { snapshot ->
        runOnUiThread {
            statusText.text = "Status: ${snapshot.status}"
            scoreText.text = snapshot.score ?: "—  :  —"
            clockText.text = "Match clock: ${snapshot.clock ?: "—"}"
            ocrText.text = snapshot.rawText?.let { "OCR: $it" } ?: "OCR: Waiting for capture"
            val running = snapshot.status == "Capturing"
            startButton.isEnabled = !running
            stopButton.isEnabled = running
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scoreText = findViewById(R.id.scoreText)
        clockText = findViewById(R.id.clockText)
        ocrText = findViewById(R.id.ocrText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        MatchState.addListener(stateListener)
        stopButton.isEnabled = false

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        startButton.setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }

        stopButton.setOnClickListener {
            startService(Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            })
        }
    }

    override fun onDestroy() {
        MatchState.removeListener(stateListener)
        super.onDestroy()
    }
}
