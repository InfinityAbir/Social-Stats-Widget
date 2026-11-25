package com.abir.socialwidget

import java.util.Calendar
import java.util.TimeZone

/**
 * Posting-time reminders: the creator sets when they usually post (one or more times a day, on the
 * days they choose) and is nudged a set time before each one. Plain Kotlin, no Android types, so the
 * timing rules can be unit tested.
 */

/** Every day of the week. Bit 0 is Monday, bit 6 is Sunday. */
const val ALL_DAYS = 0b1111111

val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** How long before the posting time to remind, in minutes. */
val LEAD_OPTIONS = listOf(10, 30, 60)

const val MAX_POSTING_TIMES = 6

private const val MINUTE_MS = 60_000L

/** [times] are minutes after midnight (7:00 PM is 1140). */
data class PostingPlan(val times: List<Int>, val days: Int, val leadMin: Int)

/** [fireAt] is when to remind; [slotAt] is the posting time it is for. Both in epoch millis. */
data class Reminder(val fireAt: Long, val slotAt: Long)

/**
 * The next reminder after [now]: the soonest (slot minus lead) that is still in the future, over the
 * coming week. A slot whose reminder time has already passed is skipped, because a "30 minutes
 * before" nudge that arrives 10 minutes before is no longer what the creator asked for. Null with no
 * posting times or no days chosen.
 */
fun nextReminder(now: Long, plan: PostingPlan, zone: TimeZone = TimeZone.getDefault()): Reminder? {
    if (plan.times.isEmpty() || plan.days and ALL_DAYS == 0) return null
    var best: Reminder? = null
    for (offset in 0..7) {
        val day = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offset)
        }
        if (plan.days and (1 shl dayBit(day)) == 0) continue
        for (t in plan.times) {
            val slot = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, t / 60)
                set(Calendar.MINUTE, t % 60)
            }.timeInMillis
            val fire = slot - plan.leadMin * MINUTE_MS
            if (fire > now && (best == null || fire < best.fireAt)) best = Reminder(fire, slot)
        }
    }
    return best
}

/** Monday is 0 ... Sunday is 6. */
private fun dayBit(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

/** A reminder that arrives after the posting time is useless; skip it (for example after the phone was off). */
fun shouldRemind(now: Long, slotAt: Long): Boolean = slotAt > 0 && now < slotAt

// ---------- Wording ----------

/** "7:00 PM" for minutes after midnight. */
fun timeLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}

/** "10 min", "30 min", "1 hour": the lead time as a person says it. */
fun leadLabel(minutes: Int): String = when {
    minutes == 60 -> "1 hour"
    minutes > 60 && minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes min"
}

/**
 * How long is really left, for the notification title. The alarm can arrive a little late on a phone
 * that is saving battery, so this says "in 24 minutes" if that is the truth, not the planned lead.
 */
fun remainingLabel(remainingMs: Long): String {
    val mins = ((remainingMs + MINUTE_MS - 1) / MINUTE_MS).coerceAtLeast(1) // round up
    return when {
        mins >= 60 -> "1 hour"
        mins == 1L -> "1 minute"
        else -> "$mins minutes"
    }
}

fun reminderTitle(remainingMs: Long) = "Posting time in ${remainingLabel(remainingMs)}"

fun reminderBody(slotMinutes: Int) = "Your ${timeLabel(slotMinutes)} slot is coming up. Time to get your video or reel ready."

fun minutesOfDay(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
    val c = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** "Today at 6:30 PM", "Tomorrow at 6:30 PM", or "Thu at 6:30 PM". */
fun describeNext(now: Long, r: Reminder, zone: TimeZone = TimeZone.getDefault()): String {
    fun midnight(ms: Long) = Calendar.getInstance(zone).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = midnight(now)
    val fireDay = midnight(r.fireAt)
    val diff = Math.round((fireDay.timeInMillis - today.timeInMillis).toDouble() / (24 * 60 * MINUTE_MS)).toInt()
    val whenText = when (diff) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> DAY_NAMES[dayBit(fireDay)].take(3)
    }
    return "$whenText at ${timeLabel(minutesOfDay(r.fireAt, zone))}"
}

// ---------- Saving the list of times ----------

/** Sorted, no duplicates, valid minutes only, at most [MAX_POSTING_TIMES]. */
fun cleanTimes(times: List<Int>): List<Int> =
    times.filter { it in 0 until 1440 }.distinct().sorted().take(MAX_POSTING_TIMES)

fun addTime(times: List<Int>, minutes: Int): List<Int> = cleanTimes(times + minutes)

fun encodeTimes(times: List<Int>): String = cleanTimes(times).joinToString(",")

fun decodeTimes(text: String): List<Int> = cleanTimes(text.split(',').mapNotNull { it.trim().toIntOrNull() })
