package com.abir.socialwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "creator insights" part of the app: how the latest post compares with your usual, how fast it
 * is moving, your best recent post and time, and a 7-day trend. Everything comes from numbers the
 * app already fetched, so showing it costs no extra API calls.
 */
@Composable
internal fun InsightsSection(
    stats: Stats,
    ytPosts: List<Post>,
    fbPosts: List<Post>,
    span: Span,
    series: List<Pair<Long, Long>>,
    ytReady: Boolean,
    fbReady: Boolean,
    now: Long = System.currentTimeMillis(),
) {
    val ytHas = ytReady && stats.ytVideoId.isNotBlank()
    val fbHas = fbReady && stats.fbVideoId.isNotBlank()
    if (!ytHas && !fbHas && !ytReady && !fbReady) return

    SectionLabel("CREATOR INSIGHTS")

    if (ytHas || fbHas) {
        Card2 {
            if (ytHas) {
                PlatformBlock(
                    name = "YouTube latest video",
                    accent = YtRed,
                    views = stats.ytVideoViews,
                    postedAt = stats.ytPostedAt,
                    latestId = stats.ytVideoId,
                    rate = stats.ytRate,
                    // Likes and comments both count; an unknown one is simply left out.
                    interactions = sumKnown(stats.ytLikes, stats.ytComments),
                    posts = ytPosts,
                    now = now,
                )
            }
            if (ytHas && fbHas) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(14.dp))
            }
            if (fbHas) {
                PlatformBlock(
                    name = "Facebook latest reel",
                    accent = FbBlue,
                    views = stats.fbVideoViews,
                    postedAt = stats.fbPostedAt,
                    latestId = stats.fbVideoId,
                    rate = stats.fbRate,
                    interactions = sumKnown(stats.fbReactions, stats.fbComments, stats.fbShares),
                    posts = fbPosts,
                    now = now,
                )
            }
        }
    }

    TrendCard(stats, span, series, ytReady, fbReady)

    Text(
        "Usual is the middle result of your last 10 posts, so one viral hit does not skew it. " +
            "Early means the post is under 6 hours old and its views are still coming in. " +
            "Engagement is likes and comments as a share of views.",
        color = TextLo,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private fun sumKnown(vararg v: Long): Long = if (v.all { it < 0 }) -1 else v.filter { it >= 0 }.sum()

@Composable
private fun PlatformBlock(
    name: String,
    accent: Color,
    views: Long,
    postedAt: Long,
    latestId: String,
    rate: Long,
    interactions: Long,
    posts: List<Post>,
    now: Long,
) {
    Text(name, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))

    val avg = usualViews(posts, latestId)
    val vs = vsAverageLabel(views, avg, if (postedAt > 0) now - postedAt else -1)
    val above = isAboveAverage(views, avg)
    InsightRow(
        "Against your usual",
        vs ?: "Needs $MIN_FOR_AVERAGE older posts",
        color = when (above) {
            true -> Good
            false -> Warn
            null -> TextLo
        },
    )
    if (avg != null) InsightRow("Your usual", "${formatCount(avg)} views", color = TextHi)

    InsightRow(
        "Speed right now",
        when {
            rate < 0 -> "Measuring… (about 30 min)"
            rate == 0L -> "No new views lately"
            else -> rateLabel(rate).replace("/h", " views/h")
        },
        color = if (rate > 0) Good else TextLo,
    )
    InsightRow("Engagement", engagementLabel(interactions, views) ?: "—", color = TextHi)

    val week = postsInLastDays(posts, 7, now)
    // Only the latest few posts are fetched, so when every one of them is from this week the real
    // count may be higher: say "20+" instead of showing the cap as if it were exact.
    if (posts.isNotEmpty()) {
        InsightRow("Posts in the last 7 days", if (week >= posts.size) "$week+" else "$week", color = TextHi)
    }

    bestPost(posts)?.let { best ->
        Spacer(Modifier.height(6.dp))
        Text("Best of your last ${posts.size}", color = TextLo, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                best.title.ifBlank { "Untitled" },
                color = TextHi,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text("${formatCount(best.views)} views", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    val window = bestPostingWindow(posts, now)
    InsightRow(
        "Best time to post",
        window ?: "Needs 6+ posts over a day old",
        color = if (window != null) TextHi else TextLo,
    )
}

@Composable
private fun InsightRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

// ---------- 7-day trend ----------

@Composable
private fun TrendCard(stats: Stats, span: Span, series: List<Pair<Long, Long>>, ytReady: Boolean, fbReady: Boolean) {
    Card2 {
        Text("Last 7 days", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        val yt = series.map { it.first }.filter { it >= 0 }
        val fb = series.map { it.second }.filter { it >= 0 }
        val enough = (ytReady && yt.size >= 2) || (fbReady && fb.size >= 2)
        if (!enough) {
            Text(
                "Collecting data. The trend appears after the app has seen your numbers on two different days.",
                color = TextLo,
                fontSize = 12.sp,
            )
            return@Card2
        }
        if (ytReady) TrendRow("Subscribers", stats.ytSubs, span.yt, span.days, yt, YtRed)
        if (ytReady && fbReady) Spacer(Modifier.height(12.dp))
        if (fbReady) TrendRow("Followers", stats.fbFollowers, span.fb, span.days, fb, FbBlue)
    }
}

@Composable
private fun TrendRow(label: String, now: Long, change: Long?, days: Int, points: List<Long>, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextLo, fontSize = 12.sp)
            Text(formatCount(now), color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val text = when {
                change == null || days == 0 -> "—"
                change > 0 -> "▲ ${formatCount(change)} in ${dayWord(days)}"
                change < 0 -> "▼ ${formatCount(-change)} in ${dayWord(days)}"
                else -> "No change in ${dayWord(days)}"
            }
            Text(
                text,
                color = when {
                    change == null || change == 0L -> TextLo
                    change > 0 -> Good
                    else -> Danger
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Sparkline(points, color)
    }
}

private fun dayWord(d: Int) = if (d == 1) "1 day" else "$d days"

@Composable
private fun Sparkline(points: List<Long>, color: Color) {
    Canvas(Modifier.size(width = 120.dp, height = 44.dp)) {
        if (points.size < 2) return@Canvas
        val lo = points.min()
        val hi = points.max()
        val range = (hi - lo).coerceAtLeast(1).toFloat()
        val pad = 5.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun pt(i: Int): Offset {
            val x = pad + w * i / (points.size - 1)
            // A flat series sits in the middle instead of on the bottom edge.
            val y = if (hi == lo) size.height / 2 else pad + h * (1f - (points[i] - lo) / range)
            return Offset(x, y)
        }
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until points.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = pt(points.size - 1))
    }
}
