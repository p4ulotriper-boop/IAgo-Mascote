package com.example.iagomascote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val REQ_OVERLAY = 101
    private val REQ_AUDIO = 102
    private val REQ_PROJECTION = 103
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
        }

        val title = TextView(this).apply {
            text = "IAgo Mascote 2D"
            textSize = 24f
            setPadding(0, 0, 0, 40)
        }

        val btnOverlay = Button(this).apply {
            text = "1. Permitir Sobrepor Apps"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivityForResult(intent, REQ_OVERLAY)
                } else {
                    Toast.makeText(this@MainActivity, "Permissão já concedida!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAudio = Button(this).apply {
            text = "2. Permitir Áudio"
            setOnClickListener {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                ActivityCompat.requestPermissions(this@MainActivity, perms.toTypedArray(), REQ_AUDIO)
            }
        }

        val btnStart = Button(this).apply {
            text = "3. Iniciar IAgo na Tela"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Ative o passo 1 primeiro!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this@MainActivity, "Ative o passo 2 primeiro!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_PROJECTION)
            }
        }

        val btnStop = Button(this).apply {
            text = "Parar IAgo"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingMascotService::class.java))
            }
        }

        layout.addView(title)
        layout.addView(btnOverlay)
        layout.addView(btnAudio)
        layout.addView(btnStart)
        layout.addView(btnStop)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, FloatingMascotService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        }
    }
}
