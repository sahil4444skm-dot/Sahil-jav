/*
 * JarvisVoice.kt  -- Module 1: voice layer (wake word + session + instant stop)
 * Package ko apne app ke package se badal lein.
 *
 * ===== SETUP (ek baar) =====
 * 1) app/build.gradle(.kts) dependencies (KSP/kapt ki zaroorat NAHI):
 *      implementation("ai.picovoice:porcupine-android:3.0.2")
 *      implementation("org.vosk:vosk-android:0.3.47")
 *      implementation("net.java.dev.jna:jna:5.13.0@aar")
 *
 * 2) Picovoice Console (console.picovoice.ai, free) se:
 *      - AccessKey lein  -> neeche ACCESS_KEY mein daalein
 *      - Custom wake word banayein: "hello jarvis" aur "off jarvis"
 *        (platform: Android) -> .ppn files download karein
 *      - Inhe app/src/main/assets/ mein rakhein ye naam dekar:
 *            hello_jarvis.ppn
 *            off_jarvis.ppn
 *
 * 3) Vosk Hindi model: vosk-model-small-hi-0.22 download karein, unzip karke
 *    folder ko rename karein "model-hi" aur app/src/main/assets/model-hi/ mein rakhein.
 *
 * 4) AndroidManifest.xml (<manifest> ke andar):
 *      <uses-permission android:name="android.permission.RECORD_AUDIO"/>
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
 *      <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
 *      <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *    <application> ke andar:
 *      <service android:name=".JarvisVoiceService"
 *               android:exported="false"
 *               android:foregroundServiceType="microphone"/>
 *      <receiver android:name=".JarvisBootReceiver" android:exported="true">
 *          <intent-filter>
 *              <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *          </intent-filter>
 *      </receiver>
 *
 * 5) Kisi bhi Activity se service shuru karne ke liye (mic permission milne ke BAAD):
 *      JarvisVoiceService.start(this)
 *    Phir phone ki Settings mein app ko Battery -> "Unrestricted" karein.
 *
 * ===== KAISE KAAM KARTA HAI =====
 *  IDLE   : sirf Porcupine sunta hai (offline, kuch save/send nahi hota)
 *  "Hello Jarvis" -> ACTIVE : Vosk STT sunta hai, 5 minute ka timer
 *  Har command par timer reset. 5 min chup -> wapas IDLE.
 *  "Off Jarvis" (kisi bhi state mein) -> TURANT: bolna band, kaam cancel, IDLE.
 *  Ek hi AudioRecord stream Porcupine aur Vosk dono ko jaati hai (mic clash nahi hota).
 */
package com.example.jarvis // <-- apna package daalein

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// ---------------------------------------------------------------
// Yahan apni command logic jodein (Module 2: Gemini + AccessibilityService)
// ---------------------------------------------------------------
object JarvisCommands {
    /** Ab tak kaam cancel karne ka hook (Gemini request, automation, etc.) */
    @Volatile var cancelCurrentTask: () -> Unit = {}

    /** Jo bhi user bole wo yahan aata hai. speak() se jawab dein. */
    var onCommand: (text: String, speak: (String) -> Unit) -> Unit = { text, speak ->
        speak("Aapne kaha: $text")   // placeholder -- baad mein Gemini yahan aayega
    }
}

class JarvisVoiceService : Service() {

    private enum class State { IDLE, ACTIVE }

    companion object {
        private const val TAG = "JarvisVoice"
        private const val ACCESS_KEY = "YAHAN_PICOVOICE_ACCESS_KEY"
        private const val WAKE_FILE = "hello_jarvis.ppn"
        private const val OFF_FILE = "off_jarvis.ppn"
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIF_ID = 77

        fun start(ctx: Context) {
            val i = Intent(ctx, JarvisVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, JarvisVoiceService::class.java))
    }

    @Volatile private var state = State.IDLE
    @Volatile private var running = false
    @Volatile private var speaking = false
    @Volatile private var lastActivity = 0L

    private var porcupine: Porcupine? = null
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var audioThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground("Jarvis sun raha hai: bolein \"Hello Jarvis\"")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }

        // Vosk model background mein unpack hota hai (pehli baar thoda time lagta hai)
        StorageService.unpack(
            this, "model-hi", "model",
            { model ->
                voskModel = model
                recognizer = Recognizer(model, 16000f)
                Log.d(TAG, "Vosk model ready")
                startListening()
            },
            { e ->
                Log.e(TAG, "Vosk model error: ${e.message}")
                // Model na mile tab bhi wake word + Off Jarvis kaam karenge
                startListening()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ----------------------------- audio loop -----------------------------

    private fun startListening() {
        if (running) return
        try {
            porcupine = Porcupine.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPaths(arrayOf(copyAsset(WAKE_FILE), copyAsset(OFF_FILE)))
                .setSensitivities(floatArrayOf(0.65f, 0.75f)) // off ko thoda zyada sensitive
                .build(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Porcupine error: ${e.message}")
            updateNotification("Error: wake word shuru nahi hua (${e.message})")
            return
        }
        running = true
        audioThread = Thread(::audioLoop, "jarvis-audio").also { it.start() }
    }

    private fun audioLoop() {
        val p = porcupine ?: return
        val frameLen = p.frameLength
        val minBuf = AudioRecord.getMinBufferSize(
            p.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                p.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, frameLen * 2 * 4)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Mic permission nahi hai"); return
        }
        record.startRecording()
        val frame = ShortArray(frameLen)

        try {
            while (running) {
                val n = record.read(frame, 0, frameLen)
                if (n != frameLen) continue

                // 1) Wake / Off keyword -- HAMESHA, har state mein
                val idx = p.process(frame)
                if (idx == 1) { hardStop(); continue }              // "Off Jarvis"
                if (idx == 0 && state == State.IDLE) { activate(); continue } // "Hello Jarvis"

                if (state == State.ACTIVE) {
                    // 5 minute timeout
                    if (System.currentTimeMillis() - lastActivity > SESSION_TIMEOUT_MS) {
                        goIdle("Session khatam (5 minute). Phir se bolein: Hello Jarvis")
                        continue
                    }
                    // 2) Vosk STT (Jarvis bolte waqt ignore, taaki apni awaaz na sune)
                    if (!speaking) feedVosk(frame, n)
                }
            }
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }

    private fun feedVosk(frame: ShortArray, n: Int) {
        val rec = recognizer ?: return
        if (rec.acceptWaveForm(frame, n)) {
            val text = JSONObject(rec.result).optString("text").trim()
            if (text.isNotEmpty()) handleText(text)
        } else {
            // user bol raha hai -> session zinda rakho
            if (rec.partialResult.contains("\"partial\" : \"\"").not()) {
                lastActivity = System.currentTimeMillis()
            }
        }
    }

    private fun handleText(text: String) {
        lastActivity = System.currentTimeMillis()
        val t = text.lowercase()
        // Backup raasta: agar Porcupine ne miss kiya to STT se bhi pakdo
        if (t.contains("off jarvis") || t.contains("of jarvis") || t.contains("ऑफ जार्विस") ||
            t.contains("ऑफ जारविस") || t.contains("बंद जार्विस")) {
            hardStop(); return
        }
        Log.d(TAG, "Command: $text")
        // Blocking kaam ke liye alag thread, audio loop kabhi block na ho
        Thread { JarvisCommands.onCommand(text, ::speak) }.start()
    }

    // ----------------------------- state changes -----------------------------

    private fun activate() {
        recognizer?.reset()
        state = State.ACTIVE
        lastActivity = System.currentTimeMillis()
        updateNotification("Jarvis ACTIVE (5 min)  |  band karne ke liye: Off Jarvis")
        speak("Ji, boliye")
    }

    /** "Off Jarvis": TURANT sab band, koi wait nahi */
    private fun hardStop() {
        tts?.stop()
        speaking = false
        try { JarvisCommands.cancelCurrentTask() } catch (_: Exception) {}
        recognizer?.reset()
        state = State.IDLE
        updateNotification("Jarvis sun raha hai: bolein \"Hello Jarvis\"")
        Log.d(TAG, "Off Jarvis -> IDLE")
    }

    private fun goIdle(msg: String) {
        state = State.IDLE
        recognizer?.reset()
        updateNotification("Jarvis sun raha hai: bolein \"Hello Jarvis\"")
        speak(msg)
    }

    // ----------------------------- TTS -----------------------------

    private fun speak(text: String) {
        if (!ttsReady) return
        speaking = true
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { speaking = false; recognizer?.reset() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { speaking = false }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-utt")
    }

    // ----------------------------- helpers -----------------------------

    /** Assets se .ppn file ko internal storage mein copy karke path deta hai */
    private fun copyAsset(name: String): String {
        val out = File(filesDir, name)
        if (!out.exists()) {
            assets.open(name).use { inp -> FileOutputStream(out).use { inp.copyTo(it) } }
        }
        return out.absolutePath
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jarvis Voice", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun startAsForeground(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        running = false
        try { audioThread?.join(500) } catch (_: Exception) {}
        tts?.shutdown()
        recognizer?.close()
        voskModel?.close()
        porcupine?.delete()
        super.onDestroy()
    }
}

/** Phone restart ke baad Jarvis apne aap sunna shuru kare */
class JarvisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Android 14+ mein boot se mic-service shuru karna restricted ho sakta hai;
            // aisa ho to user ko ek baar app kholna padega.
            try { JarvisVoiceService.start(context) } catch (e: Exception) {
                Log.e("JarvisVoice", "Boot start nahi hua: ${e.message}")
            }
        }
    }
}
