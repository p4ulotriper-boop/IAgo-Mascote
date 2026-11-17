package com.example.iagomascote

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.math.sqrt

class FloatingMascotService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mascotView: IAgoView
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            startAudioCapture()
        }

        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            mascotView = IAgoView(this)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                360,
                420,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            mascotView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(mascotView, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(mascotView, params)
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) return

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(16000)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        isRunning = true
        audioRecord?.startRecording()

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = sqrt(sum / read)
                    val openAmount = ((rms - 150.0) / 1800.0).toFloat().coerceIn(0f, 1f)
                    mascotView.post {
                        mascotView.setMouthOpen(openAmount)
                    }
                }
            }
        }.start()
    }

    private fun startForegroundNotification() {
        val channelId = "iago_mascot_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "IAgo Mascote", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("IAgo está ativo")
                .setContentText("Toque e arraste o mascote na tela")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("IAgo está ativo")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        }
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        mediaProjection?.stop()
        if (::windowManager.isInitialized && ::mascotView.isInitialized) {
            windowManager.removeView(mascotView)
        }
    }

    class IAgoView(context: Context) : View(context) {
        private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E88E5") }
        private val darkBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1565C0") }
        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") }
        private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A80") }

        private var idlePhase = 0f
        private var mouthOpen = 0f

        init {
            ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    idlePhase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun setMouthOpen(amount: Float) {
            mouthOpen = amount
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bobY = sin(idlePhase) * 10f
            val armAngle = sin(idlePhase * 2) * 22f

            canvas.save()
            canvas.translate(0f, bobY)

            // Corpo do gato azul
            canvas.drawRoundRect(110f, 220f, 250f, 360f, 60f, 60f, bluePaint)

            // Braço esquerdo (fixo)
            canvas.drawRoundRect(80f, 235f, 120f, 315f, 20f, 20f, darkBluePaint)

            // Braço direito (mexendo continuamente)
            canvas.save()
            canvas.rotate(armAngle, 245f, 245f)
            canvas.drawRoundRect(235f, 235f, 275f, 320f, 20f, 20f, darkBluePaint)
            canvas.restore()

            // Orelhas pontudas
            val leftEar = Path().apply {
                moveTo(100f, 130f)
                lineTo(115f, 45f)
                lineTo(165f, 100f)
                close()
            }
            val rightEar = Path().apply {
                moveTo(260f, 130f)
                lineTo(245f, 45f)
                lineTo(195f, 100f)
                close()
            }
            canvas.drawPath(leftEar, darkBluePaint)
            canvas.drawPath(rightEar, darkBluePaint)

            // Cabeça
            canvas.drawCircle(180f, 155f, 85f, bluePaint)

            // Olhos
            canvas.drawCircle(150f, 140f, 18f, whitePaint)
