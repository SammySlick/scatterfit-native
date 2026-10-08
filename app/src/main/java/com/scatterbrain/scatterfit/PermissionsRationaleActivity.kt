package com.scatterbrain.scatterfit

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.content.Intent
import android.widget.TextView
import androidx.health.connect.client.HealthConnectClient

/**
 * HC-mandatory rationale screen: Health Connect opens this when the user taps
 * the privacy-policy link on its permissions screen. Also the fallback entry
 * point if the request dialog misbehaves. Plain views — no Compose dependency.
 */
class PermissionsRationaleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF0D0E10.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ScatterFit reads your health data"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = ("ScatterFit uses Health Connect to read steps, sleep, heart rate, " +
                "weight and workouts from the apps you already use. Nothing is uploaded " +
                "anywhere — the data stays on this phone and in your own account.")
            setTextColor(0xFFB9BDC1.toInt())
            textSize = 15f
            setPadding(0, pad, 0, pad)
        })
        root.addView(Button(this).apply {
            text = "Open Health Connect settings"
            setOnClickListener {
                startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            }
        })
        setContentView(root)
    }
}
