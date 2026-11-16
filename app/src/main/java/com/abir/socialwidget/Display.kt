package com.abir.socialwidget

/** What to show on the widgets and tiles, plus the alert switches. Chosen in the app. */
data class Display(
    val ytViews: Boolean = true,
    val ytComments: Boolean = true,
    val ytLikes: Boolean = false,
    val ytTitle: Boolean = true,
    val ytPosted: Boolean = true,
    val ytGrowth: Boolean = true,
    val fbViews: Boolean = true,
    val fbComments: Boolean = true,
    val fbReactions: Boolean = false,
    val fbShares: Boolean = false,
    val fbTitle: Boolean = true,
    val fbPosted: Boolean = true,
    val fbGrowth: Boolean = true,
    val notifyMilestones: Boolean = false,
    val notifyFastRise: Boolean = false,
    /** Views per hour a latest video must gain to trigger the fast-rise alert. */
    val riseThreshold: Int = 500,
) {
    val anyAlerts get() = notifyMilestones || notifyFastRise
}

val RISE_THRESHOLDS = listOf(100, 500, 1000, 5000)

/** One small stat on a card: an icon and a number. */
data class Metric(val iconRes: Int, val value: Long, val desc: String)

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
