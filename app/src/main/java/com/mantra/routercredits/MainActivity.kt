package com.mantra.routercredits

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var driver: Driver
    private lateinit var big: TextView
    private lateinit var small: TextView
    private lateinit var history: TextView
    private lateinit var events: TextView
    private lateinit var check: Button
    private lateinit var inbox: Button
    @Volatile private var running = false

    private val prefs by lazy { getSharedPreferences("router", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)  // the first line names the app
        store = Store(this)
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24))
            // the focus stays here, so the page opens at the top and not scrolled to the password field
            isFocusableInTouchMode = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        fun text(size: Float, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (mono) typeface = Typeface.MONOSPACE
            col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Router Credits"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = "Settings"
            background = null
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        col.addView(header)
        text(13f).text = "TP-Link 192.168.1.1 · Telemach SIM · STANJE to 13880"
        big = text(44f, bold = true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        small = text(14f).apply { gravity = Gravity.CENTER_HORIZONTAL }

        check = Button(this).apply { text = "Check now"; setOnClickListener { runCheck() } }
        col.addView(check, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        inbox = Button(this).apply { text = "Read the router's inbox into the log"; setOnClickListener { runInbox() } }
        col.addView(inbox)

        web = WebView(this)
        col.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        driver = Driver(web, store, { Settings.password(prefs) }, { Settings.watch(prefs) })
        driver.setup()

        text(16f, bold = true).text = "Checks"
        history = text(13f, mono = true)
        text(16f, bold = true).text = "Log"
        events = text(11f, mono = true).apply { setTextColor(Color.GRAY) }

        // Android 15 draws the app under the status and navigation bars: keep the content clear of them.
        val pad = col.paddingTop
        col.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(v.paddingLeft, pad + bars.top, v.paddingRight, pad + bars.bottom)
            insets
        }
        setContentView(ScrollView(this).apply { addView(col) })
        col.requestFocus()
        refresh()
    }

    private fun sizeWeb() {
        val show = running && Settings.watch(prefs)
        web.layoutParams = web.layoutParams.apply {
            height = if (show) (resources.displayMetrics.heightPixels * 0.55).toInt() else 1
        }
    }

    private fun runCheck() = job("Checking…") {
        val t0 = System.currentTimeMillis()
        val rec = try {
            val b = driver.balance()
            val secs = (System.currentTimeMillis() - t0) / 100 / 10.0
            store.log("BALANCE %.2f MB left of %.2f at %s (%.1f s)".format(b.leftMb, b.totalMb, b.at, secs))
            b.json().put("checked", Store.now()).put("seconds", secs).also { tell(b) }
        } catch (e: Exception) {
            store.log("FAILED ${why(e)}")
            JSONObject().put("checked", Store.now()).put("error", why(e))
        }
        store.check(rec)
    }

    /** The result to Marko's phone, from the router's own SIM. A failure here never spoils the check. */
    private fun tell(b: Balance) {
        val to = Settings.myNumber(prefs)
        if (!Settings.smsMe(prefs)) return
        if (to.isEmpty()) { store.log("no phone number in Settings: the result is not sent by SMS"); return }
        val eur = b.eur?.let { " · %.2f EUR".format(it) } ?: ""
        try {
            driver.sms(to, "Router: %.1f GB left (%.0f of %.0f MB used, Telemach %s)%s".format(
                b.leftGb, b.usedMb, b.totalMb, b.at.substringAfter(' '), eur))
        } catch (e: Exception) {
            store.log("the SMS to $to FAILED: ${why(e)}")
        }
    }

    private fun runInbox() = job("Reading…") {
        try { driver.dumpInbox() } catch (e: Exception) { store.log("FAILED ${why(e)}") }
    }

    private fun why(e: Exception) = if (e is Fail) e.message else "${e.javaClass.simpleName}: ${e.message}"

    /** One router job at a time (the router allows one session), on a worker thread. */
    private fun job(label: String, work: () -> Unit) {
        if (running) return
        if (Settings.password(prefs).isEmpty()) {  // nothing to log in with yet: Settings first
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        running = true
        check.isEnabled = false
        inbox.isEnabled = false
        check.text = label
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sizeWeb()
        Thread {
            work()
            runOnUiThread {
                running = false
                check.isEnabled = true
                inbox.isEnabled = true
                check.text = "Check now"
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                sizeWeb()
                refresh()
            }
        }.start()
    }

    private fun refresh() {
        val last = store.lastBalance()
        if (last == null) {
            big.text = "—"
            small.text = "No balance measured yet. Press Check now."
        } else {
            big.text = "%.1f GB left".format(last.getDouble("left_gb"))
            val eur = if (last.isNull("eur")) "" else " · %.2f EUR".format(last.getDouble("eur"))
            small.text = "%.0f of %.0f MB used · Telemach %s · checked %s%s".format(
                last.getDouble("used_mb"), last.getDouble("total_mb"), last.getString("at").substringAfter(' '),
                last.getString("checked"), eur)
        }
        history.text = store.checks(30).joinToString("\n") { c ->
            if (c.has("error")) "${c.getString("checked")}  FAILED ${c.getString("error")}"
            else "${c.getString("checked")}  %6.1f GB left  (%.0f s)".format(c.getDouble("left_gb"), c.optDouble("seconds"))
        }.ifEmpty { "none yet" }
        events.text = store.tail(80).reversed().joinToString("\n")
    }

    override fun onResume() {
        super.onResume()
        if (!running) refresh()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
