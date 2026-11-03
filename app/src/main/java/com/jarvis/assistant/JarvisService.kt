package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Toast
import java.util.Locale

/**
 * Foreground service: "Hello Jarvis" sunta hai (Android ka SpeechRecognizer loop),
 * phir command leta hai, kaam karta hai aur bolkar jawab deta hai.
 */
class JarvisService : Service(), TextToSpeech.OnInitListener {

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var speaking = false
    @Volatile private var ringing = false
    private var running = false
    private var awakeUntil = 0L
    private var cb: Any? = null
    private var legacy: Any? = null
    private val wakeRegex = Regex("(jarvis|jarwis|jarvish|jarves|jervis|jarbis|garvis|jarvi)")

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis sun raha hai")
            .setContentText("\"Hello Jarvis\" bolo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)

        tts = TextToSpeech(this, this)
        running = true
        registerCalls()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Is phone mein speech recognition nahi hai (Google app install/update karo)", Toast.LENGTH_LONG).show()
        }
        h.postDelayed({ listen() }, 1500)
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val r = t.setLanguage(Locale("hi", "IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale("en", "IN"))
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { done() }
        })
        ttsReady = true
    }

    private fun done() {
        if (tts?.isSpeaking == true) return
        speaking = false
        if (running) h.postDelayed({ listen() }, 400)
    }

    fun say(msg: String) {
        if (!ttsReady) return
        speaking = true
        val m = if (msg.length > 700) msg.substring(0, 700) else msg
        h.post {
            try { rec?.cancel() } catch (e: Exception) { }
            tts?.speak(m, TextToSpeech.QUEUE_ADD, null, "j" + System.nanoTime())
        }
    }

    private fun listen() {
        if (!running || speaking) return
        h.post {
            try { rec?.destroy() } catch (e: Exception) { }
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            rec = r
            r.setRecognitionListener(listener)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            try { r.startListening(i) } catch (e: Exception) { h.postDelayed({ listen() }, 1500) }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}

        override fun onError(e: Int) {
            if (!running || speaking) return
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Toast.makeText(this@JarvisService, "Mic permission do", Toast.LENGTH_LONG).show()
                stopSelf(); return
            }
            h.postDelayed({ listen() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 300L)
        }

        override fun onResults(r: Bundle?) {
            val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (list.isNullOrEmpty()) { listen(); return }
            val text = if (ringing || System.currentTimeMillis() < awakeUntil) list[0]
            else list.firstOrNull { wakeRegex.containsMatchIn(CommandRouter.normalize(it)) } ?: list[0]
            onText(text)
        }
    }

    private fun onText(raw: String) {
        val t = CommandRouter.normalize(raw)
        when {
            ringing -> callCommand(t)
            System.currentTimeMillis() < awakeUntil -> { awakeUntil = 0; execCmd(t) }
            else -> {
                val m = wakeRegex.find(t)
                if (m == null) { listen(); return }
                val rest = t.substring(m.range.last + 1).trim().removePrefix("ji").trim()
                if (rest.isBlank()) { awakeUntil = System.currentTimeMillis() + 12000; say("Ji boliye") }
                else execCmd(rest)
            }
        }
    }

    private fun execCmd(cmd: String) {
        Thread {
            try { CommandRouter.handle(applicationContext, cmd) { say(it) } } catch (e: Exception) { say("Kuch gadbad ho gayi.") }
            h.post { if (!speaking) listen() }
        }.start()
    }

    // ---------- incoming call ----------
    private fun callCommand(t: String) {
        val tm = getSystemService(TelecomManager::class.java)
        try {
            when {
                Regex("uth|receive|answer|pick|\\bhaan\\b|\\bhan\\b").containsMatchIn(t) -> {
                    tm.acceptRingingCall(); ringing = false; say("Call utha li.")
                }
                Regex("kat|kaat|cut|reject|disconnect|decline|\\bnahi\\b|\\bna\\b").containsMatchIn(t) -> {
                    if (Build.VERSION.SDK_INT >= 28) tm.endCall()
                    ringing = false; say("Call kaat di.")
                }
                else -> listen()
            }
        } catch (e: SecurityException) {
            ringing = false; say("Call ki permission nahi hai.")
        }
    }

    private fun callState(state: Int) {
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            ringing = true
            say("Call aa rahi hai. Uthaun ya kaat doon?")
        } else {
            ringing = false
        }
    }

    @Suppress("NewApi", "DEPRECATION")
    private fun registerCalls() {
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) {
                val c = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) { callState(state) }
                }
                cb = c
                tm.registerTelephonyCallback(mainExecutor, c)
            } else {
                val l = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) { callState(state) }
                }
                legacy = l
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) { }
    }

    @Suppress("NewApi", "DEPRECATION")
    override fun onDestroy() {
        running = false
        try { rec?.destroy() } catch (e: Exception) { }
        tts?.shutdown()
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) (cb as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            else (legacy as? PhoneStateListener)?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
