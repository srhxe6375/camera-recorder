package com.example.videorecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerQuality: Spinner
    private lateinit var etMaxFileSize: EditText
    private lateinit var cbFrontCamera: CheckBox
    private lateinit var cbMuteAudio: CheckBox
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnToggleTorch: Button

    private val PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + Manifest.permission.POST_NOTIFICATIONS
        } else {
            it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerQuality = findViewById(R.id.spinnerQuality)
        etMaxFileSize = findViewById(R.id.etMaxFileSize)
        cbFrontCamera = findViewById(R.id.cbFrontCamera)
        cbMuteAudio = findViewById(R.id.cbMuteAudio)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnToggleTorch = findViewById(R.id.btnToggleTorch)

        // क्वालिटी ऑप्शन सेट करें
        val qualities = arrayOf("SD (Lowest Size)", "HD (720p)", "FHD (1080p)", "HIGHEST")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualities)
        spinnerQuality.adapter = adapter

        btnStart.setOnClickListener {
            if (hasPermissions()) {
                startRecordingService()
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, CameraService::class.java).apply {
                action = CameraService.ACTION_STOP
            }
            startService(intent)
        }

        btnToggleTorch.setOnClickListener {
            val intent = Intent(this, CameraService::class.java).apply {
                action = CameraService.ACTION_TOGGLE_TORCH
            }
            startService(intent)
        }
    }

    private fun startRecordingService() {
        val selectedQuality = when (spinnerQuality.selectedItemPosition) {
            1 -> "HD"
            2 -> "FHD"
            3 -> "HIGHEST"
            else -> "SD"
        }

        val maxMb = etMaxFileSize.text.toString().toLongOrNull() ?: 0L
        val maxSizeBytes = maxMb * 1024 * 1024 // Bytes में कन्वर्ट करें

        val intent = Intent(this, CameraService::class.java).apply {
            action = CameraService.ACTION_START
            putExtra("QUALITY", selectedQuality)
            putExtra("IS_FRONT", cbFrontCamera.isChecked)
            putExtra("IS_MUTED", cbMuteAudio.isChecked)
            putExtra("MAX_SIZE_BYTES", maxSizeBytes)
        }

        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "रिकॉर्डिंग सर्विस शुरू हो गई", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions() = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, 101)
    }
}
