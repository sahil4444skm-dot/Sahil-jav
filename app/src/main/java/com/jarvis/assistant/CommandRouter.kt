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
