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
    private const val KEEP_DAYS = 30

    internal class Day(
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

    /** Subscriber / follower counts at the end of each of the last [n] recorded days, oldest first. -1 = unknown. */
    @Synchronized
    fun series(c: Context, n: Int = 7): List<Pair<Long, Long>> =
        load(c).takeLast(n).map { it.ytClose to it.fbClose }

    /** Change over up to [days] days; see [spanChange]. */
    @Synchronized
    fun span(c: Context, s: Stats, days: Int = 7, now: Long = System.currentTimeMillis()): Span =
        spanChange(load(c), fmt().format(Date(now)), s.ytSubs, s.fbFollowers, days)

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

/** Change over a stretch of days. [days] is how far back the starting point really was (0 = no data yet). */
data class Span(val yt: Long?, val fb: Long?, val days: Int)

private const val DAY_MS_H = 24 * 3_600_000L

/**
 * Change since the start of the oldest recorded day that is at most [days] days back, so a fresh
 * install with three days of history reports three days instead of nothing.
 */
internal fun spanChange(history: List<History.Day>, today: String, ytNow: Long, fbNow: Long, days: Int): Span {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayMs = runCatching { f.parse(today)?.time }.getOrNull() ?: return Span(null, null, 0)
    var base: History.Day? = null
    var baseAge = 0
    for (d in history) {
        val ms = runCatching { f.parse(d.day)?.time }.getOrNull() ?: continue
        val age = Math.round((todayMs - ms).toDouble() / DAY_MS_H).toInt()
        if (age in 1..days && age > baseAge) {
            base = d
            baseAge = age
        }
    }
    if (base == null) return Span(null, null, 0)
    fun delta(now: Long, open: Long, close: Long): Long? {
        val start = if (open >= 0) open else close
        return if (now < 0 || start < 0) null else now - start
    }
    return Span(delta(ytNow, base.ytOpen, base.ytClose), delta(fbNow, base.fbOpen, base.fbClose), baseAge)
}
