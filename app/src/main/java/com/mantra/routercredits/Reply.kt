package com.mantra.routercredits

import org.json.JSONObject

/** One balance, parsed from Telemach's reply to STANJE. */
data class Balance(
    val at: String,          // "2026-11-03 13:05", Telemach's own time inside the SMS
    val usedMb: Double,
    val totalMb: Double,
    val eur: Double?,
    val bonusEur: Double?,
    val text: String,
) {
    val leftMb get() = totalMb - usedMb
    val leftGb get() = leftMb / 1024

    fun json(): JSONObject = JSONObject()
        .put("at", at).put("used_mb", usedMb).put("total_mb", totalMb)
        .put("left_mb", Math.round(leftMb * 100) / 100.0).put("left_gb", Math.round(leftGb * 100) / 100.0)
        .put("eur", eur ?: JSONObject.NULL).put("bonus_eur", bonusEur ?: JSONObject.NULL).put("text", text)

    companion object {
        fun from(o: JSONObject) = Balance(
            o.getString("at"), o.getDouble("used_mb"), o.getDouble("total_mb"),
            o.optDouble("eur").takeUnless { it.isNaN() }, o.optDouble("bonus_eur").takeUnless { it.isNaN() },
            o.optString("text"),
        )
    }
}

object Reply {
    private val REPLY = Regex(
        """do (\d{1,2})\.(\d{1,2})\.(\d{4}) u (\d{1,2}):(\d{2}) iskoristili ste ([\d.]+) od ukupno ([\d.]+) MB""",
        RegexOption.IGNORE_CASE)
    private val EUR = Regex("""stanje racuna je ([\d.,]+) EUR(?: i bonus ([\d.,]+) EUR)?""", RegexOption.IGNORE_CASE)

    /** "Postovani, do 14.10.2026 u 11:57 iskoristili ste 73062.82 od ukupno 102400.00 MB ..." */
    fun parse(text: String?): Balance? {
        val m = REPLY.find(text ?: return null) ?: return null
        val (d, mo, y, h, mi, used, total) = m.destructured
        val e = EUR.find(text)
        return Balance(
            at = "%04d-%02d-%02d %02d:%s".format(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi),
            usedMb = used.toDouble(), totalMb = total.toDouble(),
            eur = e?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull(),
            bonusEur = e?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull(),
            text = text,
        )
    }
}
