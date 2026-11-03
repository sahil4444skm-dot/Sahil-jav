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
