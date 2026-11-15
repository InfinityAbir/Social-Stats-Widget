package com.abir.socialwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Today's change in subscribers (yt) and followers (fb). Null = not enough history yet. */
data class Growth(val yt: Long? = null, val fb: Long? = null)

/**
 * One small record per calendar day: the first and the last subscriber / follower count seen that day.
 * "Today's growth" is the current count minus yesterday's last count (or, with no record from yesterday,
 * minus the first count seen today).
 */
object History {
    private const val KEY = "history"
    private const val KEEP_DAYS = 14

    private class Day(
        val day: String,
        var ytOpen: Long, var ytClose: Long,
        var fbOpen: Long, var fbClose: Long,
    )

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun load(c: Context): MutableList<Day> {
        val raw = Store.sp(c).getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) {
                val o = arr.getJSONObject(it)
                Day(
                    o.getString("d"),
                    o.optLong("yo", -1), o.optLong("yc", -1),
                    o.optLong("fo", -1), o.optLong("fc", -1),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    private fun save(c: Context, days: List<Day>) {
        val arr = JSONArray()
        days.forEach {
            arr.put(
                JSONObject().put("d", it.day)
                    .put("yo", it.ytOpen).put("yc", it.ytClose)
                    .put("fo", it.fbOpen).put("fc", it.fbClose),
            )
        }
        Store.sp(c).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun record(c: Context, s: Stats, now: Long = System.currentTimeMillis()) {
        if (s.ytSubs < 0 && s.fbFollowers < 0) return
        val today = fmt().format(Date(now))
        val days = load(c)
        val d = days.lastOrNull { it.day == today }
            ?: Day(today, -1, -1, -1, -1).also { days.add(it) }
        if (s.ytSubs >= 0) {
            if (d.ytOpen < 0) d.ytOpen = s.ytSubs
            d.ytClose = s.ytSubs
        }
        if (s.fbFollowers >= 0) {
            if (d.fbOpen < 0) d.fbOpen = s.fbFollowers
            d.fbClose = s.fbFollowers
        }
        days.sortBy { it.day }
        while (days.size > KEEP_DAYS) days.removeAt(0)
        save(c, days)
    }

    @Synchronized
    fun growth(c: Context, s: Stats, now: Long = System.currentTimeMillis()): Growth {
        val days = load(c)
        if (days.isEmpty()) return Growth()
        val f = fmt()
        val today = f.format(Date(now))
        val yesterday = f.format(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.time)
        val prev = days.lastOrNull { it.day == yesterday }
        val cur = days.lastOrNull { it.day == today }

        fun delta(current: Long, prevClose: Long?, todayOpen: Long?): Long? {
            if (current < 0) return null
            val base = prevClose?.takeIf { it >= 0 } ?: todayOpen?.takeIf { it >= 0 } ?: return null
            return current - base
        }
        return Growth(
            yt = delta(s.ytSubs, prev?.ytClose, cur?.ytOpen),
            fb = delta(s.fbFollowers, prev?.fbClose, cur?.fbOpen),
        )
    }
}
