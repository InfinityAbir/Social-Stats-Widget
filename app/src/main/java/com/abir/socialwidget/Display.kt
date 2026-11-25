package com.abir.socialwidget

/** What to show on the widgets and tiles, plus the alert switches. Chosen in the app. */
data class Display(
    val ytViews: Boolean = true,
    val ytComments: Boolean = true,
    val ytLikes: Boolean = false,
    val ytTitle: Boolean = true,
    val ytPosted: Boolean = true,
    val ytGrowth: Boolean = true,
    /** "2.1× usual": the latest video against your last ten. */
    val ytVsAvg: Boolean = true,
    /** "+120/h": views gained per hour right now. */
    val ytSpeed: Boolean = true,
    val fbViews: Boolean = true,
    val fbComments: Boolean = true,
    val fbReactions: Boolean = false,
    val fbShares: Boolean = false,
    val fbTitle: Boolean = true,
    val fbPosted: Boolean = true,
    val fbGrowth: Boolean = true,
    val fbVsAvg: Boolean = true,
    val fbSpeed: Boolean = true,
    val notifyMilestones: Boolean = false,
    val notifyFastRise: Boolean = false,
    /** Views per hour a latest video must gain to trigger the fast-rise alert. */
    val riseThreshold: Int = 500,
    /** A new comment on the latest video or reel. */
    val notifyComments: Boolean = false,
    /** A connection (expired token, bad key) that has been failing for hours. */
    val notifyStale: Boolean = false,
    /** A nudge shortly before the creator's usual posting times. */
    val notifyReminder: Boolean = false,
    /** Posting times, minutes after midnight (7:00 PM is 1140). */
    val reminderTimes: List<Int> = emptyList(),
    /** Days of the week the reminder applies to; bit 0 is Monday. */
    val reminderDays: Int = ALL_DAYS,
    /** How many minutes before the posting time to remind. */
    val reminderLead: Int = 30,
) {
    val plan get() = PostingPlan(reminderTimes, reminderDays, reminderLead)
    val anyAlerts get() = notifyMilestones || notifyFastRise || notifyComments || notifyStale || notifyReminder
}

val RISE_THRESHOLDS = listOf(100, 500, 1000, 5000)

/** One small stat on a card: an icon and a number. */
data class Metric(val iconRes: Int, val value: Long, val desc: String)

/**
 * The small badge line under a card: "2.1× usual · +120/h". Each part follows its own switch and is
 * left out when there is nothing to say. [compact] drops the "early" note for the narrow widget.
 */
fun insightParts(
    views: Long, posts: List<Post>, latestId: String, postedAt: Long, rate: Long,
    showVsAvg: Boolean, showSpeed: Boolean, compact: Boolean = false, now: Long = System.currentTimeMillis(),
): List<String> {
    val parts = ArrayList<String>(2)
    if (showVsAvg) {
        vsAverageLabel(views, usualViews(posts, latestId), if (postedAt > 0) now - postedAt else -1, showEarly = !compact)
            ?.let { parts += it }
    }
    if (showSpeed) rateLabel(rate).takeIf { it.isNotEmpty() }?.let { parts += it }
    return parts
}

/** The same badges on one line, for the narrow widget. */
fun insightLine(
    views: Long, posts: List<Post>, latestId: String, postedAt: Long, rate: Long,
    showVsAvg: Boolean, showSpeed: Boolean, compact: Boolean = false, now: Long = System.currentTimeMillis(),
): String = insightParts(views, posts, latestId, postedAt, rate, showVsAvg, showSpeed, compact, now).joinToString(" · ")

fun ytMetrics(s: Stats, d: Display): List<Metric> = buildList {
    if (d.ytViews) add(Metric(R.drawable.ic_eye, s.ytVideoViews, "Views"))
    if (d.ytComments && s.ytComments >= 0) add(Metric(R.drawable.ic_comment, s.ytComments, "Comments"))
    if (d.ytLikes && s.ytLikes >= 0) add(Metric(R.drawable.ic_like, s.ytLikes, "Likes"))
}

fun fbMetrics(s: Stats, d: Display): List<Metric> = buildList {
    if (d.fbViews) add(Metric(R.drawable.ic_eye, s.fbVideoViews, "Views"))
    if (d.fbComments && s.fbComments >= 0) add(Metric(R.drawable.ic_comment, s.fbComments, "Comments"))
    if (d.fbReactions && s.fbReactions >= 0) add(Metric(R.drawable.ic_like, s.fbReactions, "Reactions"))
    if (d.fbShares && s.fbShares >= 0) add(Metric(R.drawable.ic_share, s.fbShares, "Shares"))
}

/** "Posted 3h ago" for a publish time in epoch millis; "" when unknown. */
fun postedAgo(postedAt: Long, now: Long = System.currentTimeMillis()): String {
    if (postedAt <= 0) return ""
    val mins = (now - postedAt) / 60_000
    return "Posted " + when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> {
            val h = mins / 60
            val m = mins % 60
            if (h < 6 && m > 0) "${h}h ${m}m ago" else "${h}h ago"
        }
        else -> "${mins / (24 * 60)}d ago"
    }
}

/** "▲ 12 today" / "▼ 3 today"; null when there is nothing to say. */
fun growthText(delta: Long?): String? = when {
    delta == null || delta == 0L -> null
    delta > 0 -> "▲ ${formatCount(delta)} today"
    else -> "▼ ${formatCount(-delta)} today"
}
