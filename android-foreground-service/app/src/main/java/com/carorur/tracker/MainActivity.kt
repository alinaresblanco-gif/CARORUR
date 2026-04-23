package com.carorur.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestNeededPermissionsThenStart()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, LocationForegroundService::class.java))
            updateStatus("Estado: detenido")
        }

        updateStatus()
    }

    private fun requestNeededPermissionsThenStart() {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= 33) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= 29) {
            required.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        startTrackerService()
    }

    private fun startTrackerService() {
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        updateStatus("Estado: activo en foreground")
    }

    private fun updateStatus(overrideText: String? = null) {
        txtStatus.text = overrideText ?: "Estado: listo"
    }
}
