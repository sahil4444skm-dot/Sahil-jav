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
