package com.mantra.routercredits

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The application log, in the app's own folder on shared storage
 * (Android/data/com.mantra.routercredits/files, readable over adb):
 *   router-credits.log  every step, one line each
 *   checks.jsonl        one line per check: the balance, or the error
 */
class Store(context: Context) {
    private val dir: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val events = File(dir, "router-credits.log")
    private val checks = File(dir, "checks.jsonl")

    @Synchronized fun log(msg: String) {
        events.appendText("${now()} $msg\n")
        android.util.Log.i("RouterCredits", msg)
    }

    @Synchronized fun check(o: JSONObject) {
        checks.appendText(o.toString() + "\n")
    }

    /** The newest checks first. */
    fun checks(n: Int = 50): List<JSONObject> =
        if (!checks.exists()) emptyList()
        else checks.readLines().filter { it.isNotBlank() }.takeLast(n).reversed()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }

    fun lastBalance(): JSONObject? = checks(500).firstOrNull { it.has("left_gb") }

    fun tail(n: Int = 40): List<String> = if (!events.exists()) emptyList() else events.readLines().takeLast(n)

    companion object {
        fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    }
}
