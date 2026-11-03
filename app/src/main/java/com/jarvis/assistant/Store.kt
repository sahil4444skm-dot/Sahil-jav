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
