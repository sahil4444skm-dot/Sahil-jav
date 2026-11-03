# Jarvis - saari files (copy karke GitHub par banao)
GitHub repo mein **Add file > Create new file** dabao. Naam mein neeche likha path daalo (`/` likhne se folder khud ban jaata hai), phir code copy-paste karke Commit karo. Kul 14 files hain.

## 1. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JarvisAssistant"
include(":app")
```

## 2. `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

## 3. `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

## 4. `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvis.assistant"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// Koi KSP / kapt / Room / Hilt nahi - isliye KSP wala error aayega hi nahi.
dependencies { }
```

## 5. `.github/workflows/build.yml`

```yaml
name: Build APK
on:
  push:
    branches: [ main, master ]
  workflow_dispatch:
jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'
      - name: Build debug APK
        run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: Jarvis-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

## 6. `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
        <package android:name="com.whatsapp" />
        <package android:name="com.google.android.youtube" />
    </queries>

    <application
        android:label="@string/app_name"
        android:allowBackup="false"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".JarvisService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

        <service
            android:name=".JarvisAccessibilityService"
            android:exported="true"
            android:label="Jarvis Control"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_config" />
        </service>
    </application>
</manifest>
```

## 7. `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Jarvis</string>
    <string name="acc_desc">Jarvis ko screen par tap, scroll aur type karne deta hai</string>
</resources>
```

## 8. `app/src/main/res/xml/accessibility_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100"
    android:description="@string/acc_desc" />
```

## 9. `app/src/main/java/com/jarvis/assistant/Store.kt`

```kotlin
package com.jarvis.assistant

import android.content.Context

/** Chhota sa storage: API key + Jarvis ka "seekha hua" (do / dont / alias). */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = p(c).getString("key", "") ?: ""
    fun setApiKey(c: Context, v: String) = p(c).edit().putString("key", v.trim()).apply()

    fun model(c: Context): String = p(c).getString("model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
    fun setModel(c: Context, v: String) = p(c).edit().putString("model", v.trim()).apply()

    /** kind = "do" (yaad rakho), "dont" (mat karna), "alias" (k=>v) */
    fun list(c: Context, kind: String): List<String> =
        (p(c).getString("l_$kind", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun add(c: Context, kind: String, v: String) {
        val l = list(c, kind).toMutableList()
        if (v !in l) l.add(v)
        p(c).edit().putString("l_$kind", l.takeLast(100).joinToString("\n")).apply()
    }

    fun aliases(c: Context): List<Pair<String, String>> =
        list(c, "alias").mapNotNull { val i = it.indexOf("=>"); if (i > 0) it.substring(0, i) to it.substring(i + 2) else null }

    fun addAlias(c: Context, k: String, v: String) = add(c, "alias", "$k=>$v")

    fun clearRules(c: Context) =
        p(c).edit().remove("l_do").remove("l_dont").remove("l_alias").apply()

    fun last(c: Context): String = p(c).getString("last", "") ?: ""
    fun setLast(c: Context, v: String) = p(c).edit().putString("last", v).apply()
}
```

## 10. `app/src/main/java/com/jarvis/assistant/AiClient.kt`

```kotlin
package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "Jarvis/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** returns (httpCode, body) */
    fun postJson(url: String, json: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    }
}

/** Duniya ki knowledge + baat-cheet: Gemini API (free key aistudio.google.com se). */
object AiClient {
    fun ask(ctx: Context, q: String): String {
        val key = Store.apiKey(ctx)
        if (key.isBlank()) return "Pehle app kholkar Gemini API key daaliye, phir main duniya bhar ke sawaal ka jawab de paunga."

        val sys = StringBuilder()
        sys.append("Tum Jarvis ho, ek voice assistant. Hinglish (Roman Hindi) mein jawab do. ")
        sys.append("Jawab chhota rakho (2-3 vakya), bina markdown, bina emoji, kyunki use bolkar sunaya jayega. ")
        sys.append("Taaza jaankari ke liye Google search use karo. ")
        val dos = Store.list(ctx, "do")
        val donts = Store.list(ctx, "dont")
        if (dos.isNotEmpty()) sys.append("Yaad rakho (user ne bataya): ").append(dos.joinToString("; ")).append(". ")
        if (donts.isNotEmpty()) sys.append("User ne mana kiya hai: ").append(donts.joinToString("; ")).append(". ")

        fun body(search: Boolean): String {
            val o = JSONObject()
            o.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys.toString()))))
            o.put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", q)))))
            if (search) o.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            return o.toString()
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${Store.model(ctx)}:generateContent?key=$key"
        var r = Net.postJson(url, body(true))
        if (r.first !in 200..299) r = Net.postJson(url, body(false))
        if (r.first !in 200..299) return "AI se jawab nahi mila (code ${r.first}). API key ya model ka naam check kijiye."
        return try {
            val parts = JSONObject(r.second).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            sb.toString().replace("*", "").trim().ifBlank { "Mujhe jawab samajh nahi aaya." }
        } catch (e: Exception) {
            "Jawab padhne mein dikkat aayi."
        }
    }
}
```

## 11. `app/src/main/java/com/jarvis/assistant/JarvisAccessibilityService.kt`

```kotlin
package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Screen par tap / scroll / type / button-click karta hai - kisi bhi app mein. */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }

    /** action ko 'first' ms baad try karo, fail ho to 'delay' ms par dobara, max 'attempts' baar. */
    fun retry(attempts: Int, delay: Long, first: Long, onFail: (() -> Unit)? = null, action: () -> Boolean) {
        var n = 0
        val r = object : Runnable {
            override fun run() {
                n++
                val ok = try { action() } catch (e: Exception) { false }
                if (!ok) {
                    if (n < attempts) h.postDelayed(this, delay) else onFail?.invoke()
                }
            }
        }
        h.postDelayed(r, first)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, pred: (AccessibilityNodeInfo) -> Boolean) {
        if (n == null) return
        if (pred(n)) out.add(n)
        for (i in 0 until n.childCount) collect(n.getChild(i), out, pred)
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = node
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            c = c.parent
        }
        val r = Rect(); node.getBoundsInScreen(r)
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    fun clickText(labels: List<String>, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val want = labels.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (want.isEmpty()) return false
        val out = mutableListOf<AccessibilityNodeInfo>()
        collect(root, out) { n ->
            val s = listOf(n.text, n.contentDescription).mapNotNull { it?.toString()?.lowercase() }
            s.any { x -> want.any { w -> if (exact) x == w else x.contains(w) } }
        }
        val n = out.firstOrNull() ?: return false
        return click(n)
    }

    /** YouTube search result ka pehla video khol do. */
    fun clickFirstVideo(): Boolean {
        val root = rootInActiveWindow ?: return false
        val list = mutableListOf<AccessibilityNodeInfo>()
        collect(root, list) { n ->
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            n.isClickable && d.length > 25 && !d.contains("shorts") &&
                (d.contains(" view") || d.contains("watching") || d.contains("minute") || d.contains("second"))
        }
        val best = list.map { n -> val r = Rect(); n.getBoundsInScreen(r); n to r }
            .filter { it.second.top > 150 }
            .minByOrNull { it.second.top } ?: return false
        return click(best.first)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null) {
            val out = mutableListOf<AccessibilityNodeInfo>()
            collect(root, out) { it.isEditable }
            target = out.firstOrNull()
        }
        if (target == null) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun size() = resources.displayMetrics.let { it.widthPixels.toFloat() to it.heightPixels.toFloat() }

    fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
        return dispatchGesture(g, null, null)
    }

    fun tapScreenFraction(fx: Float, fy: Float): Boolean { val (w, hh) = size(); return tap(w * fx, hh * fy) }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long = 350): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build()
        return dispatchGesture(g, null, null)
    }

    /** down = niche scroll (content upar jaata hai) */
    fun scroll(down: Boolean): Boolean {
        val (w, hh) = size()
        return if (down) swipe(w / 2, hh * 0.72f, w / 2, hh * 0.28f) else swipe(w / 2, hh * 0.28f, w / 2, hh * 0.72f)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun screenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
}
```

## 12. `app/src/main/java/com/jarvis/assistant/CommandRouter.kt`

```kotlin
package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import org.json.JSONObject
import java.util.Locale

/** Bola hua command samajhkar kaam karta hai. */
object CommandRouter {

    private val dev = mapOf(
        "जार्विस" to "jarvis", "जारविस" to "jarvis", "जर्विस" to "jarvis", "जार्वस" to "jarvis",
        "यूट्यूब" to "youtube", "यूट्यब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सएप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "खोलो" to "kholo", "खोल" to "khol", "करो" to "karo", "कर दो" to "kar do",
        "मौसम" to "mausam", "बारिश" to "barish", "कॉल" to "call", "फोन" to "phone",
        "चलाओ" to "chalao", "सर्च" to "search", "वीडियो" to "video", "क्रोम" to "chrome", "कैमरा" to "camera"
    )

    fun normalize(s: String): String {
        var t = s.lowercase(Locale.ROOT)
        for ((k, v) in dev) t = t.replace(k, v)
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun handle(ctx: Context, raw: String, say: (String) -> Unit) {
        var t = normalize(raw)
        if (t.isBlank()) return
        for ((k, v) in Store.aliases(ctx)) if (t.contains(k)) t = t.replace(k, v)

        if (learn(ctx, t, say)) return

        for (r in Store.list(ctx, "dont")) {
            if (r.isNotBlank() && t.contains(r)) { say("Aapne mana kiya tha, isliye main ye nahi karunga."); return }
        }
        Store.setLast(ctx, t)
        try { route(ctx, t, say) } catch (e: Exception) { say("Kuch gadbad ho gayi: ${e.message}") }
    }

    // ---------- seekhna ----------
    private fun learn(ctx: Context, t: String, say: (String) -> Unit): Boolean {
        val alias = Regex("jab (?:main )?(.+?) (?:bolun|bolu|kahun|bol dun|bolta hu|bolta hoon) (?:to|toh) (.+)").find(t)
        if (alias != null) {
            Store.addAlias(ctx, alias.groupValues[1].trim(), alias.groupValues[2].trim())
            say("Theek hai, seekh liya."); return true
        }
        if (t.contains("sab bhool jao") || t.contains("sab bhul jao")) {
            Store.clearRules(ctx); say("Sab bhool gaya."); return true
        }
        val dontKw = listOf("mat karna", "mat karo", "mat kholna", "mat chalana", "dobara mat", "never do", "mana hai")
        val dk = dontKw.firstOrNull { t.contains(it) }
        if (dk != null) {
            var rule = t.replace(dk, "").replace(Regex("\\b(ye|yeh)\\b"), "").replace(Regex("\\s+"), " ").trim()
            if (rule.isBlank()) rule = Store.last(ctx)
            if (rule.isNotBlank()) { Store.add(ctx, "dont", rule); say("Theek hai, ab ye nahi karunga."); return true }
        }
        val rk = listOf("yaad rakho", "yaad rakhna", "remember that").firstOrNull { t.contains(it) }
        if (rk != null) {
            val fact = t.substringAfter(rk).trim()
            if (fact.isNotBlank()) { Store.add(ctx, "do", fact); say("Yaad rakh liya."); return true }
        }
        return false
    }

    private fun start(ctx: Context, i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun clean(t: String, noise: Set<String>): String =
        t.split(" ").filter { it.isNotBlank() && it !in noise }.joinToString(" ").trim()

    // ---------- routing ----------
    private fun route(ctx: Context, t: String, say: (String) -> Unit) {
        val w = t.split(" ").toSet()
        fun any(vararg k: String) = k.any { t.contains(it) }
        fun anyW(vararg k: String) = k.any { it in w }
        val acc = JarvisAccessibilityService.instance

        when {
            any("weather", "mausam", "barish", "temperature") -> weather(say)
            any("whatsapp") -> whatsapp(ctx, t, say)
            any("call karo", "call kar", "phone karo", "phone kar", "dial") -> callContact(ctx, t, say)
            any("youtube") -> youtube(ctx, t, say)

            any("torch", "flashlight", "flash light") -> {
                val on = !any("band", "off", "bujha")
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(cm.cameraIdList[0], on)
                say(if (on) "Torch chalu." else "Torch band.")
            }
            any("volume") -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val dir = when {
                    any("mute", "silent") -> AudioManager.ADJUST_MUTE
                    any("kam", "ghata", "decrease", "down", "low") -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_RAISE
                }
                repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
                say("Volume theek kar diya.")
            }
            any("screenshot") -> { need(acc, say) { it.screenshot() } }
            anyW("back", "peeche", "wapas") -> need(acc, say) { it.back() }
            any("home screen", "go home", "home jao") || t == "home" -> need(acc, say) { it.home() }
            anyW("recent", "recents") -> need(acc, say) { it.recents() }
            any("notification") -> need(acc, say) { it.notifications() }
            any("scroll", "neeche", "niche", "upar") -> {
                val up = any("scroll up", "upar", "up ")
                need(acc, say) { it.scroll(!up) }
            }
            t.startsWith("type ") || any("likho") -> {
                val txt = if (t.startsWith("type ")) t.removePrefix("type ") else t.substringAfter("likho")
                need(acc, say) { if (!it.typeText(txt.trim())) say("Type karne ki jagah nahi mili.") }
            }
            t.startsWith("click ") || t.startsWith("tap ") || any("dabao") -> {
                val name = clean(t, setOf("click", "tap", "dabao", "on", "par", "pe", "ko", "karo", "kar"))
                need(acc, say) { if (!it.clickText(listOf(name))) say("$name screen par nahi dikha.") }
            }
            any("wifi", "wi-fi") -> {
                start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
                say("Wifi khol diya.")
            }
            any("bluetooth") -> { start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); say("Bluetooth settings khol di.") }
            anyW("settings") -> { start(ctx, Intent(Settings.ACTION_SETTINGS)); say("Settings khol di.") }

            anyW("open", "kholo", "khol", "launch", "chalu", "khulo") || t.startsWith("open ") -> openApp(ctx, t, say)

            t.startsWith("google") || t.startsWith("search") || anyW("dhundo", "khojo") -> {
                val q = clean(t, setOf("google", "search", "karo", "kar", "pe", "par", "dhundo", "khojo", "jarvis", "for", "me", "ke", "liye"))
                start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
                say("Google par $q search kar raha hoon.")
            }
            else -> say(AiClient.ask(ctx, t))
        }
    }

    private fun need(acc: JarvisAccessibilityService?, say: (String) -> Unit, f: (JarvisAccessibilityService) -> Unit) {
        if (acc == null) say("Pehle Accessibility mein Jarvis Control ON kijiye.") else f(acc)
    }

    // ---------- weather ----------
    private fun weather(say: (String) -> Unit) {
        val j = JSONObject(Net.get("https://wttr.in/?format=j1"))
        val cur = j.getJSONArray("current_condition").getJSONObject(0)
        val desc = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value")
        val temp = cur.getString("temp_C"); val feels = cur.getString("FeelsLikeC")
        val hum = cur.getString("humidity"); val rain = cur.optString("precipMM", "0")
        val city = try { j.getJSONArray("nearest_area").getJSONObject(0).getJSONArray("areaName").getJSONObject(0).getString("value") } catch (e: Exception) { "Yahan" }
        var chance = 0
        try {
            val hourly = j.getJSONArray("weather").getJSONObject(0).getJSONArray("hourly")
            for (i in 0 until hourly.length()) chance = maxOf(chance, hourly.getJSONObject(i).optString("chanceofrain", "0").toInt())
        } catch (e: Exception) { }
        val raining = (rain.toDoubleOrNull() ?: 0.0) > 0.0 || desc.lowercase().contains("rain") || desc.lowercase().contains("drizzle")
        val b = if (raining) "Haan, abhi barish ho rahi hai." else "Abhi barish nahi ho rahi."
        say("$city mein abhi $desc hai. Temperature $temp degree, mehsoos $feels degree, humidity $hum percent. $b Aaj barish ki sambhavna $chance percent hai.")
    }

    // ---------- contacts ----------
    private fun findNumber(ctx: Context, name: String): String? {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val n = name.lowercase().trim()
        if (n.isEmpty()) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0)?.lowercase()?.contains(n) == true) return c.getString(1)
            }
        }
        return null
    }

    private fun digits(num: String): String {
        var d = num.filter { it.isDigit() }
        if (d.length == 10) d = "91$d" // India default
        return d
    }

    private val noiseName = setOf("whatsapp", "par", "pe", "on", "ko", "to", "kar", "do", "karo", "message", "msg", "send",
        "bhej", "bhejo", "bhejna", "text", "hello", "jarvis", "please", "ek", "a", "call", "phone", "dial", "se", "baat", "kijiye")

    private fun callContact(ctx: Context, t: String, say: (String) -> Unit) {
        val name = clean(t, noiseName)
        val num = findNumber(ctx, name)
        if (num == null) { say("$name naam ka contact nahi mila."); return }
        if (ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            say("Call ki permission nahi hai."); return
        }
        say("$name ko call laga raha hoon.")
        start(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num.filter { it.isDigit() || it == '+' })))
    }

    private fun whatsapp(ctx: Context, t: String, say: (String) -> Unit) {
        val m = Regex("\\s(?:ki|ke liye|that)\\s(.+)$").find(t)
        if (m == null) { say("Message samajh nahi aaya. Aise bolo: Faizan ko WhatsApp karo ki main late hoon."); return }
        val msg = m.groupValues[1].trim()
        val name = clean(t.substring(0, m.range.first), noiseName)
        val num = findNumber(ctx, name)
        if (num == null) { say("$name naam ka contact nahi mila."); return }
        val uri = Uri.parse("https://wa.me/${digits(num)}?text=${Uri.encode(msg)}")
        val i = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
        try { start(ctx, i) } catch (e: Exception) { start(ctx, Intent(Intent.ACTION_VIEW, uri)) }
        val acc = JarvisAccessibilityService.instance
        if (acc == null) { say("Message khul gaya hai, par Accessibility ON nahi hai isliye bhej nahi paaya."); return }
        say("$name ko message bhej raha hoon.")
        acc.retry(12, 800, 2500, onFail = { say("Send button nahi mila.") }) { acc.clickText(listOf("send", "भेजें"), exact = true) }
    }

    // ---------- YouTube ----------
    private val noiseYt = setOf("open", "youtube", "you", "tube", "yt", "pe", "par", "me", "mein", "search", "karo", "kar", "do",
        "kijiye", "aur", "and", "then", "first", "pehla", "pehle", "video", "chalao", "chala", "play", "khol", "kholo", "khulo",
        "mere", "liye", "ke", "ka", "ki", "ko", "the", "a", "scroll", "down", "tap", "click", "hello", "jarvis", "please", "on")

    private fun youtube(ctx: Context, t: String, say: (String) -> Unit) {
        val q = clean(t, noiseYt)
        val uri = if (q.isBlank()) Uri.parse("https://www.youtube.com")
        else Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        val i = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube")
        try { start(ctx, i) } catch (e: Exception) { start(ctx, Intent(Intent.ACTION_VIEW, uri)) }
        if (q.isBlank()) { say("YouTube khol diya."); return }
        say("YouTube par $q chala raha hoon.")
        val acc = JarvisAccessibilityService.instance
        if (acc == null) { say("Accessibility ON nahi hai, isliye video khud nahi chala paaunga."); return }
        acc.retry(8, 1000, 3500, onFail = { acc.tapScreenFraction(0.5f, 0.33f) }) { acc.clickFirstVideo() }
    }

    // ---------- open app ----------
    private fun openApp(ctx: Context, t: String, say: (String) -> Unit) {
        val name = clean(t, setOf("open", "kholo", "khol", "launch", "start", "chalu", "khulo", "app", "ko", "please",
            "hello", "jarvis", "kar", "do", "karo", "the"))
        if (name.isBlank()) { say("Kaun sa app kholun?"); return }
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: apps.firstOrNull { val l = it.loadLabel(pm).toString().lowercase(); l.length > 2 && name.contains(l) }
        if (hit == null) { say("$name naam ka app nahi mila."); return }
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
        if (li == null) { say("Ye app khul nahi sakta."); return }
        start(ctx, li)
        say("${hit.loadLabel(pm)} khol diya.")
    }
}
```

## 13. `app/src/main/java/com/jarvis/assistant/MainActivity.kt`

```kotlin
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
```

## 14. `app/src/main/java/com/jarvis/assistant/JarvisService.kt`

```kotlin
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
```
