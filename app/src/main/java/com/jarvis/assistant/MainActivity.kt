package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun tv(s: String, size: Float = 15f): TextView = TextView(this).apply { text = s; textSize = size }.also { root.addView(it) }
        fun btn(label: String, f: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { f() } }) }

        tv("Jarvis", 30f)
        status = tv("")
        tv("\nStep 1: Gemini API key (free: aistudio.google.com)")
        val key = EditText(this).apply { setText(Store.apiKey(this@MainActivity)); hint = "API key"; isSingleLine = true }
        root.addView(key)
        val model = EditText(this).apply { setText(Store.model(this@MainActivity)); hint = "model"; isSingleLine = true }
        root.addView(model)
        btn("Key save karo") {
            Store.setApiKey(this, key.text.toString()); Store.setModel(this, model.text.toString())
            Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
        }

        tv("\nStep 2: Permissions")
        btn("Permissions do (mic, contacts, call...)") {
            val p = mutableListOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }
        btn("Accessibility ON karo (Jarvis Control)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("'Display over other apps' do") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("Battery optimization hatao") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        tv("\nStep 3: Jarvis chalao")
        btn("START - 'Hello Jarvis' sunna shuru") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Pehle mic permission do", Toast.LENGTH_LONG).show()
            } else startForegroundService(Intent(this, JarvisService::class.java))
        }
        btn("STOP") { stopService(Intent(this, JarvisService::class.java)) }

        tv("\nBolkar dekho:\n• Hello Jarvis, YouTube par Harmeet ka gana chalao\n• Hello Jarvis, Faizan ko WhatsApp karo ki main aaj nahi aa sakta\n• Hello Jarvis, aaj mausam kaisa hai\n• Hello Jarvis, torch chalu karo\n• Hello Jarvis, yaad rakho main Delhi mein rehta hoon\n• Hello Jarvis, ye mat karna (pichla kaam blocked)")

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val acc = if (en.contains(packageName)) "ON" else "OFF"
        val mic = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "OK" else "NAHI"
        val ov = if (Settings.canDrawOverlays(this)) "OK" else "NAHI"
        status.text = "Mic: $mic   Accessibility: $acc   Overlay: $ov"
    }
}
