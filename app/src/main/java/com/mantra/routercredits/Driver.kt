package com.mantra.routercredits

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Fail(msg: String) : Exception(msg)

/**
 * The router's own web page in our own WebView, driven step by step: the same steps, selectors and
 * parsing as router-sms on the Mac (TP-Link TL-MR110-Outdoor, Telemach SIM). Every look is a short
 * script; the waiting is done here, on a worker thread, because the router's login reloads the page
 * and ends any script still running in it.
 */
class Driver(
    private val web: WebView,
    private val store: Store,
    private val password: () -> String,
    private val visible: () -> Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loaded = CountDownLatch(1)

    @SuppressLint("SetJavaScriptEnabled")
    fun setup() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        // A desktop browser: the router then shows its PC page, the one whose selectors are proven.
        web.settings.userAgentString =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:132.0) Gecko/20100101 Firefox/132.0"
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
        }
    }

    // ------------------------------------------------------------ the browser, from the worker thread

    private fun open(url: String = ROUTER) {
        loaded = CountDownLatch(1)
        main.post { web.loadUrl(url) }
        if (!loaded.await(30, TimeUnit.SECONDS)) throw Fail("the router's page did not load (is the phone on its Wi-Fi?)")
        Thread.sleep(500)
    }

    /** Run one short script in the page; its value comes back as a Kotlin value (String, Boolean, JSONArray…). */
    private fun js(body: String, vararg args: Any): Any? {
        val a = args.joinToString(", ") { if (it is String) JSONObject.quote(it) else it.toString() }
        val src = "(function(){ const vis = e => !!(e && e.offsetParent); $body })($a)"
        val wrapped = "(function(){ try { return JSON.stringify((function(){ return $src; })()); } catch (e) { return JSON.stringify({__err: String(e)}); } })()"
        val done = CountDownLatch(1)
        var out: String? = null
        main.post { web.evaluateJavascript(wrapped) { out = it; done.countDown() } }
        if (!done.await(15, TimeUnit.SECONDS)) throw Fail("the page did not answer")
        // evaluateJavascript hands back the JSON of our JSON string.
        val inner = (out?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() } as? String) ?: return null
        val v = runCatching { JSONTokener(inner).nextValue() }.getOrNull()
        if (v is JSONObject && v.has("__err")) throw Fail("page error: " + v.getString("__err"))
        return if (v == JSONObject.NULL) null else v
    }

    private fun truthy(v: Any?) = v != null && v != false && v != "" && v != 0

    private fun poll(seconds: Double = 6.0, look: () -> Any?): Any? {
        val t0 = System.currentTimeMillis()
        while (true) {
            val v = look()
            if (truthy(v) || System.currentTimeMillis() - t0 > seconds * 1000) return v
            Thread.sleep(250)
        }
    }

    private fun pause() { if (visible()) Thread.sleep(700) }

    // ------------------------------------------------------------ the router

    private fun state(): String = js("""
        const pw = document.getElementById("pc-login-password");
        if (vis(pw)) return "login";
        const a = [...document.querySelectorAll("a")].find(a => vis(a) && a.innerText.trim() == "Log out");
        return a ? "in" : "loading";""") as? String ?: "loading"

    private fun ready() {
        repeat(2) {
            var s = "loading"
            for (i in 0 until 40) {
                s = state()
                if (s != "loading") break
                Thread.sleep(250)
            }
            if (s == "in") return
            if (s == "login") { login(); return }
            open()  // stuck: load the page again once
        }
        throw Fail("router page did not load")
    }

    private fun type(id: String, value: String) {
        val ms = if (visible()) 110L else 0L
        js("""const f = document.getElementById(arguments[0]); f.focus(); f.value = "";""", id)
        if (ms > 0) for (c in value) {
            js("""const f = document.getElementById(arguments[0]); f.value += arguments[1];
                  f.dispatchEvent(new Event("input", {bubbles: true}));""", id, c.toString())
            Thread.sleep(ms)
        } else js("""document.getElementById(arguments[0]).value = arguments[1];""", id, value)
        js("""const f = document.getElementById(arguments[0]);
              for (const t of ["input", "change", "keyup", "blur"]) f.dispatchEvent(new Event(t, {bubbles: true}));""", id)
    }

    private fun login() {
        if (!truthy(poll(5.0) { js("""return vis(document.getElementById("pc-login-password"));""") }))
            throw Fail("router login: no password field")
        val pw = password()
        if (pw.isEmpty()) throw Fail("no router password set")
        type("pc-login-password", pw)
        pause()
        js("""document.getElementById("pc-login-btn").click(); return true;""")
        var forced = false
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 20_000) {
            Thread.sleep(400)
            try {
                // "Only one device can log in at a time … force the other device to log off?"
                if (js("""const y = document.getElementById("confirm-yes"); if (vis(y)) { y.click(); return "forced"; } return "";""") == "forced")
                    forced = true
                if (state() == "in") {
                    store.log("logged in to the router" + if (forced) " (the other device logged off)" else "")
                    return
                }
            } catch (_: Fail) { /* the page is reloading */ }
        }
        val err = runCatching { js(ERR_JS) as? String }.getOrNull()
        throw Fail("router login failed: " + (err?.ifEmpty { null } ?: "no Log out link after 20 s"))
    }

    private fun clickText(t: String) = js("""
        const all = [...document.querySelectorAll("a, span, li, button")].filter(e => vis(e) && e.innerText && e.innerText.trim() == arguments[0]);
        if (!all.length) return "";
        all[all.length - 1].click(); return "clicked";""", t)

    private fun hasLink(t: String) = js("""
        return [...document.querySelectorAll("a")].some(a => vis(a) && a.innerText.trim() == arguments[0]);""", t) == true

    /** Advanced, then SMS, then the item (Inbox, New Message), one look at a time. */
    private fun tryMenu(name: String): Boolean {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 12_000) {
            if (hasLink(name)) { clickText(name); Thread.sleep(800); pause(); return true }
            if (hasLink("SMS")) clickText("SMS") else clickText("Advanced")
            Thread.sleep(600)
        }
        return false
    }

    private fun menu(name: String) {
        if (tryMenu(name)) return
        // the session may have ended under us (somebody else logged in): log in again, once
        open(); ready()
        if (!tryMenu(name)) throw Fail("no menu item $name")
    }

    private fun send(to: String, text: String) {
        menu("New Message")
        if (!truthy(poll { js("""return vis(document.getElementById("toNumber"));""") })) throw Fail("no New Message form")
        type("toNumber", to); pause()
        type("inputContent", text); pause()
        js("""document.getElementById("send").click(); return true;""")
        Thread.sleep(1500)
        (js(ERR_JS) as? String)?.takeIf { it.isNotEmpty() }?.let { store.log("after send the page says: $it") }
        store.log("SMS sent to $to: $text")
    }

    /** Inbox rows: [index, from, preview, received]. A fresh page every time: the router lists a new SMS only to a page loaded after it came. */
    private fun rows(reload: Boolean): List<List<String>> {
        if (reload) { open(); ready() }
        menu("Inbox")
        val arr = poll(5.0) {
            (js("""return [...document.querySelectorAll("tbody tr")].filter(r => r.querySelector("span[id^=msg_]")).map(r => {
                const c = [...r.cells].map(c => c.innerText.trim());
                return [r.querySelector("span[id^=msg_]").id.slice(4), c[2] || "", c[3] || "", c[4] || ""]; });""") as? JSONArray)
                ?.takeIf { it.length() > 0 }
        } as? JSONArray ?: return emptyList()
        return (0 until arr.length()).map { i -> arr.getJSONArray(i).let { r -> (0 until r.length()).map { r.getString(it) } } }
    }

    private fun read(idx: String): String {
        if (!truthy(poll { js("""const s = document.getElementById("msg_" + arguments[0]); if (!vis(s)) return false; s.click(); return true;""", idx) }))
            throw Fail("no message $idx in the inbox")
        val text = poll { js("""const p = document.getElementById("msgContent"); return vis(p) ? p.textContent.trim() : "";""") } as? String ?: ""
        if (visible() && Reply.parse(text) != null) Thread.sleep(SHOW_MS)  // the answer stays in front of him
        js("""const b = document.getElementById("back"); if (vis(b)) { b.click(); return true; } return false;""")
        poll { js("""return vis(document.getElementById("msg_" + arguments[0]));""", idx) }
        return text
    }

    /** Log in, send STANJE to 13880, wait for Telemach's reply, parse it. Runs on a worker thread. */
    fun balance(): Balance {
        store.log("check started")
        open()
        ready()
        val before = rows(false).map { it.drop(1) }.toSet()
        send(BALANCE_TO, BALANCE_TEXT)
        val sent = System.currentTimeMillis()
        val looked = mutableSetOf<List<String>>()
        while (System.currentTimeMillis() - sent < REPLY_WAIT_MS) {
            Thread.sleep(REPLY_POLL_MS)
            for (r in rows(true)) {
                val key = r.drop(1)
                if (key in before || key in looked) continue
                looked += key
                val (idx, from, prev) = r
                if (!from.lowercase().contains("telemach") && !prev.lowercase().startsWith("postovani")) continue
                val text = read(idx)
                val b = Reply.parse(text)
                if (b == null) { store.log("a new Telemach message that is not a balance: ${text.take(120)}"); continue }
                // Only a reply written after STANJE was sent counts (an old one once came back when the list shifted).
                val at = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(b.at)?.time ?: 0
                if (at < sent - 120_000) { store.log("an old Telemach reply skipped (from ${b.at})"); continue }
                return b
            }
        }
        throw Fail("no reply from Telemach within ${REPLY_WAIT_MS / 1000} s")
    }

    companion object {
        const val ROUTER = "http://192.168.1.1/"
        const val BALANCE_TO = "13880"
        const val BALANCE_TEXT = "STANJE"
        const val REPLY_WAIT_MS = 60_000L
        const val REPLY_POLL_MS = 3_000L
        const val SHOW_MS = 5_000L
        const val ERR_JS = """return [...document.querySelectorAll("[class*=error], [class*=tips]")].filter(vis)
            .map(e => e.innerText.trim()).filter(t => t).join(" / ");"""
    }
}
