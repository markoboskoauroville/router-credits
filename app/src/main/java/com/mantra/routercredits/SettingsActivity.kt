package com.mantra.routercredits

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** What the app needs from Marko, kept on the phone only: never in the code. */
object Settings {
    fun password(p: SharedPreferences) = p.getString("password", "").orEmpty()
    fun myNumber(p: SharedPreferences) = p.getString("myNumber", "").orEmpty().trim()
    fun smsMe(p: SharedPreferences) = p.getBoolean("smsMe", true)
    fun watch(p: SharedPreferences) = p.getBoolean("watch", true)
}

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("router", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        fun add(v: android.view.View, top: Int = 8) =
            col.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) })
        fun label(t: String) = add(TextView(this).apply { text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }, 20)

        add(TextView(this).apply {
            text = "Settings"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTypeface(typeface, Typeface.BOLD)
        }, 0)

        label("Router password (http://192.168.1.1)")
        val pass = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Settings.password(prefs))
        }
        add(pass)
        val showPass = CheckBox(this).apply {
            text = "Show the password"
            setOnCheckedChangeListener { _, on ->
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.setSelection(pass.text.length)
            }
        }
        add(showPass, 0)

        label("My phone number: the router's SIM texts the result here")
        val number = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "+385…"
            setText(Settings.myNumber(prefs))
        }
        add(number)
        val smsMe = CheckBox(this).apply { text = "Send the result by SMS after each check"; isChecked = Settings.smsMe(prefs) }
        add(smsMe)
        val watch = CheckBox(this).apply { text = "Show the browser while it works"; isChecked = Settings.watch(prefs) }
        add(watch)

        add(Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit()
                    .putString("password", pass.text.toString())
                    .putString("myNumber", number.text.toString().trim())
                    .putBoolean("smsMe", smsMe.isChecked)
                    .putBoolean("watch", watch.isChecked)
                    .apply()
                finish()
            }
        }, 24)

        val pad = col.paddingTop
        col.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(v.paddingLeft, pad + bars.top, v.paddingRight, pad + bars.bottom)
            insets
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
