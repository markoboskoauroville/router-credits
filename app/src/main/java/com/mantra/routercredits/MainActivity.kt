package com.mantra.routercredits

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
    private lateinit var watch: CheckBox
    private lateinit var pass: EditText
    @Volatile private var running = false

    private val prefs by lazy { getSharedPreferences("router", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        fun text(size: Float, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (mono) typeface = Typeface.MONOSPACE
            col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        text(14f).text = "Router Credits · TP-Link 192.168.1.1 · Telemach"
        big = text(44f, bold = true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        small = text(14f).apply { gravity = Gravity.CENTER_HORIZONTAL }

        check = Button(this).apply { text = "Check now"; setOnClickListener { runCheck() } }
        col.addView(check, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        watch = CheckBox(this).apply {
            text = "Show the browser while it works"
            isChecked = prefs.getBoolean("watch", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("watch", on).apply(); sizeWeb() }
        }
        col.addView(watch)

        pass = EditText(this).apply {
            hint = "Router password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("password", DEFAULT_PASSWORD))
        }
        col.addView(pass)

        web = WebView(this)
        col.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        driver = Driver(web, store, { prefs.getString("password", DEFAULT_PASSWORD) ?: "" }, { watch.isChecked })
        driver.setup()

        text(16f, bold = true).text = "Checks"
        history = text(13f, mono = true)
        text(16f, bold = true).text = "Log"
        events = text(11f, mono = true).apply { setTextColor(Color.GRAY) }

        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun sizeWeb() {
        val show = running && watch.isChecked
        web.layoutParams = web.layoutParams.apply {
            height = if (show) (resources.displayMetrics.heightPixels * 0.55).toInt() else 1
        }
    }

    private fun runCheck() {
        if (running) return
        prefs.edit().putString("password", pass.text.toString()).apply()
        running = true
        check.isEnabled = false
        check.text = "Checking…"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sizeWeb()
        Thread {
            val t0 = System.currentTimeMillis()
            val rec = try {
                val b = driver.balance()
                val secs = (System.currentTimeMillis() - t0) / 100 / 10.0
                store.log("BALANCE %.2f MB left of %.2f at %s (%.1f s)".format(b.leftMb, b.totalMb, b.at, secs))
                b.json().put("checked", Store.now()).put("seconds", secs)
            } catch (e: Exception) {
                val msg = if (e is Fail) e.message else "${e.javaClass.simpleName}: ${e.message}"
                store.log("FAILED $msg")
                JSONObject().put("checked", Store.now()).put("error", msg)
            }
            store.check(rec)
            runOnUiThread {
                running = false
                check.isEnabled = true
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
        events.text = store.tail(40).reversed().joinToString("\n")
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val DEFAULT_PASSWORD = "12345678"
    }
}
