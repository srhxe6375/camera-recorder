package com.example.videorecorder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File

class CameraService : LifecycleService() {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraControl: CameraControl? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_TORCH = "ACTION_TOGGLE_TORCH"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "camera_recording_channel"

        var isRecording = false
        var isTorchOn = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                val quality = intent.getStringExtra("QUALITY") ?: "SD"
                val isFrontCamera = intent.getBooleanExtra("IS_FRONT", false)
                val isAudioMuted = intent.getBooleanExtra("IS_MUTED", false)
                val maxSizeBytes = intent.getLongExtra("MAX_SIZE_BYTES", 0L)

                startForegroundServiceWithNotification()
                setupAndStartRecording(quality, isFrontCamera, isAudioMuted, maxSizeBytes)
            }
            ACTION_STOP -> stopRecording()
            ACTION_TOGGLE_TORCH -> toggleTorch()
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("वीडियो रिकॉर्डिंग चालू है")
            .setContentText("कैमरा बैकग्राउंड में रिकॉर्ड कर रहा है...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAndStartRecording(
        qualityStr: String,
        isFrontCamera: Boolean,
        isAudioMuted: Boolean,
        maxSizeBytes: Long
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. रिज़ॉल्यूशन क्वालिटी चुनना
            val quality = when (qualityStr) {
                "HD" -> Quality.HD
                "FHD" -> Quality.FHD
                "HIGHEST" -> Quality.HIGHEST
                else -> Quality.SD
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()

            videoCapture = VideoCapture.with(recorder)

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)
                cameraControl = camera.cameraControl

                // 2. आउटपुट फ़ाइल सेट करना
                val outputFile = File(
                    getExternalFilesDir(null),
                    "REC_${System.currentTimeMillis()}.mp4"
                )

                val fileOutputOptionsBuilder = FileOutputOptions.Builder(outputFile)

                // 3. फाइल साइज़ लिमिट (यदि सेट है)
                if (maxSizeBytes > 0) {
                    fileOutputOptionsBuilder.setFileSizeLimit(maxSizeBytes)
                }

                val pendingRecording = videoCapture?.output
                    ?.prepareRecording(this, fileOutputOptionsBuilder.build())

                if (!isAudioMuted) {
                    pendingRecording?.withAudioEnabled()
                }

                // 4. रिकॉर्डिंग शुरू करें
                activeRecording = pendingRecording?.start(ContextCompat.getMainExecutor(this)) { event: VideoRecordEvent ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (event.hasError()) {
                                activeRecording?.close()
                                activeRecording = null
                            }
                            stopSelf()
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        cameraControl?.let {
            isTorchOn = !isTorchOn
            it.enableTorch(isTorchOn)
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
