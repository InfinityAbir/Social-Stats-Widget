package com.abir.socialwidget

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Creator insights, all computed from what the app already fetches. Everything here is plain Kotlin
 * with no Android types so it can be unit tested.
 */

/** One recent video or reel. */
data class Post(val id: String, val title: String, val views: Long, val publishedAt: Long)

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

// ---------- Compared with your usual ----------

const val AVG_WINDOW = 10
const val MIN_FOR_AVERAGE = 3

/** A post younger than this has not had time to gather its views yet. */
const val EARLY_MS = 6 * HOUR_MS

/**
 * What a post of yours usually gets: the MEDIAN views of up to [AVG_WINDOW] posts before the latest.
 * The median, not the mean, because one viral hit would drag an average far above what a normal
 * post gets and make every ordinary post look weak. Null with fewer than [MIN_FOR_AVERAGE] to
 * compare against, because the middle of one or two posts says nothing.
 */
fun usualViews(posts: List<Post>, latestId: String): Long? {
    val prev = posts
        .filter { it.id != latestId && it.views >= 0 }
        .sortedByDescending { it.publishedAt }
        .take(AVG_WINDOW)
        .map { it.views }
        .sorted()
    if (prev.size < MIN_FOR_AVERAGE) return null
    val mid = prev.size / 2
    return if (prev.size % 2 == 1) prev[mid] else (prev[mid - 1] + prev[mid]) / 2
}

/** "2.1× usual", with " · early" while the post is too young to judge (unless [showEarly] is off, for tight spaces). Null when there is nothing to compare. */
fun vsAverageLabel(views: Long, average: Long?, ageMs: Long, showEarly: Boolean = true): String? {
    if (average == null || average <= 0 || views < 0) return null
    // "0× usual" reads like a bug; say what is actually going on.
    if (views == 0L) return "No views yet"
    val ratio = views.toDouble() / average
    if (ratio < 0.1) return "<0.1× usual" + if (showEarly && ageMs in 0 until EARLY_MS) " · early" else ""
    val n = if (ratio >= 10) "%.0f".format(Locale.US, ratio) else "%.1f".format(Locale.US, ratio).removeSuffix(".0")
    return "$n× usual" + if (showEarly && ageMs in 0 until EARLY_MS) " · early" else ""
}

/** True when the latest post is at or above the usual; null when unknown. */
fun isAboveAverage(views: Long, average: Long?): Boolean? =
    if (average == null || average <= 0 || views < 0) null else views >= average

// ---------- Speed ----------

/** A views count seen at a moment, kept to work out how fast the latest post is gaining. */
data class Sample(val id: String, val t: Long, val views: Long)

/** [rate] is views per hour, -1 when unknown. [sample] is what to remember for next time. */
data class VelocityStep(val rate: Long, val sample: Sample?)

private const val VEL_MIN_MS = 30 * 60_000L
private const val VEL_REBASE_MS = 90 * 60_000L

/**
 * Views per hour right now, from two readings at least 30 minutes apart. Closer than that the last
 * rate is kept, because a few minutes of data is mostly noise; past 90 minutes the baseline moves up,
 * so the rate always describes roughly the last half hour to hour and a half.
 */
fun velocityStep(prev: Sample?, prevRate: Long, id: String, views: Long, now: Long): VelocityStep {
    if (id.isBlank() || views < 0) return VelocityStep(-1, null)
    val fresh = Sample(id, now, views)
    if (prev == null || prev.id != id || prev.t <= 0 || now < prev.t) return VelocityStep(-1, fresh)
    val elapsed = now - prev.t
    if (elapsed < VEL_MIN_MS) return VelocityStep(prevRate, prev)
    val gained = (views - prev.views).coerceAtLeast(0)
    val rate = gained * HOUR_MS / elapsed
    return VelocityStep(rate, if (elapsed >= VEL_REBASE_MS) fresh else prev)
}

/** A refresh that has been "running" longer than this is assumed lost (killed, no network), so the widget stops saying so. */
const val REFRESH_STALE_MS = 90_000L

/** Whether to show "Refreshing…" on the widget for a refresh started at [startedAt] (0 = none). */
fun isRefreshing(startedAt: Long, now: Long): Boolean = startedAt > 0 && now - startedAt in 0 until REFRESH_STALE_MS

/** "+120/h"; empty when it is not moving or unknown. */
fun rateLabel(rate: Long): String = if (rate <= 0) "" else "+${formatCount(rate)}/h"

// ---------- Engagement ----------

/** Likes, comments (and shares) as a share of views, like "3.2%". Null when it cannot be worked out. */
fun engagementLabel(interactions: Long, views: Long): String? {
    if (views <= 0 || interactions < 0) return null
    val pct = interactions * 100.0 / views
    return (if (pct >= 10) "%.0f%%" else "%.1f%%").format(Locale.US, pct)
}

// ---------- Best post, best time, frequency ----------

/** The post with the most views, needing at least two posts to be a comparison. */
fun bestPost(posts: List<Post>): Post? =
    if (posts.size < 2) null else posts.filter { it.views >= 0 }.maxByOrNull { it.views }

fun postsInLastDays(posts: List<Post>, days: Int, now: Long): Int =
    posts.count { it.publishedAt > 0 && now - it.publishedAt in 0..(days * DAY_MS) }

private const val MIN_POSTS_FOR_TIME = 6
private const val MIN_IN_WINDOW = 2

/**
 * The three-hour slot of the day whose posts gathered the most views on average, like "6 PM–9 PM".
 * Only posts older than a day count (a newer one has not had its views yet), at least six of them,
 * and a slot needs at least two posts so one lucky video does not decide it. Null otherwise.
 */
fun bestPostingWindow(posts: List<Post>, now: Long, zone: TimeZone = TimeZone.getDefault()): String? {
    val mature = posts.filter { it.views >= 0 && it.publishedAt > 0 && now - it.publishedAt >= DAY_MS }
    if (mature.size < MIN_POSTS_FOR_TIME) return null
    val cal = Calendar.getInstance(zone)
    val slots = mature.groupBy {
        cal.timeInMillis = it.publishedAt
        cal.get(Calendar.HOUR_OF_DAY) / 3
    }
    val best = slots.filter { it.value.size >= MIN_IN_WINDOW }
        .maxByOrNull { (_, ps) -> ps.sumOf { it.views }.toDouble() / ps.size }
        ?: return null
    val start = best.key * 3
    return "${hourLabel(start)}–${hourLabel((start + 3) % 24)}"
}

fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

// ---------- Alerts ----------

/** How long a connection error must last before it is worth a notification. */
const val STALE_AFTER_MS = 3 * HOUR_MS

data class StaleStep(val since: Long, val notified: Boolean, val fire: Boolean)

/**
 * Tracks one platform's connection error across refreshes. Fires once per episode, after the error
 * has lasted [afterMs]; a clean refresh resets it so a later failure can fire again.
 */
fun staleStep(hasError: Boolean, since: Long, notified: Boolean, now: Long, afterMs: Long = STALE_AFTER_MS): StaleStep {
    if (!hasError) return StaleStep(0, false, false)
    val start = if (since > 0) since else now
    if (notified) return StaleStep(start, true, false)
    val fire = now - start >= afterMs
    return StaleStep(start, fire, fire)
}

/** How many new comments the same video got since last time; 0 for a first sighting or a different video. */
fun newComments(prevId: String?, prevCount: Long?, id: String, count: Long): Long {
    if (id.isBlank() || count < 0 || prevId != id || prevCount == null || prevCount < 0) return 0
    return (count - prevCount).coerceAtLeast(0)
}
