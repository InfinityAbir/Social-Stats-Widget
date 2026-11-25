package com.abir.socialwidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TextMain = Color(0xFFFFFFFF)
private val TextDim = Color(0xFFA9AEC2)

fun formatCount(n: Long): String = when {
    n < 0 -> "—"
    n >= 1_000_000_000 -> trim1(n / 1_000_000_000.0) + "B"
    n >= 1_000_000 -> trim1(n / 1_000_000.0) + "M"
    n >= 10_000 -> trim1(n / 1_000.0) + "K"
    else -> "%,d".format(Locale.US, n)
}

/** The widget's one spare line under the stats: "Posted 3h ago · 2.1× usual · +120/h · title", whichever parts are on. */
private fun widgetLine(posted: String, insight: String, title: String): String =
    listOf(posted, insight, title).filter { it.isNotBlank() }.joinToString(" · ")

private fun trim1(d: Double): String {
    val s = String.format(Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

class StatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read inside the composition and re-read on every saved change, so the widget never shows old numbers.
            val tick by prefsTicks(context).collectAsState(0L)
            val configured = remember(tick) { Store.loadConfigured(context) }
            val stats = remember(tick) { Store.loadStats(context) }
            val display = remember(tick) { Store.loadDisplay(context) }
            val growth = remember(tick) { History.growth(context, stats) }
            val ytPosts = remember(tick) { Store.loadPosts(context, "yt") }
            val fbPosts = remember(tick) { Store.loadPosts(context, "fb") }
            val refreshing = remember(tick) { isRefreshing(Store.refreshingAt(context), System.currentTimeMillis()) }
            Content(configured, stats, display, growth, ytPosts, fbPosts, refreshing)
        }
    }
}

internal val GrowthUp = Color(0xFF59D68C)
internal val GrowthDown = Color(0xFFFF8A80)

@Composable
private fun Content(
    configured: Configured, stats: Stats, display: Display, growth: Growth,
    ytPosts: List<Post>, fbPosts: List<Post>, refreshing: Boolean,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(8.dp)
            // Tapping the widget opens the app.
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Panel(
                    modifier = GlanceModifier.defaultWeight(),
                    label = "YouTube",
                    accent = YtRed,
                    iconRes = R.drawable.ic_youtube,
                    cardRes = R.drawable.card_yt,
                    configured = configured.yt,
                    big = stats.ytSubs,
                    bigLabel = "subscribers",
                    growth = if (display.ytGrowth) growth.yt else null,
                    metrics = ytMetrics(stats, display),
                    title = widgetLine(
                        if (display.ytPosted) postedAgo(stats.ytPostedAt).removePrefix("Posted ") else "",
                        insightLine(
                            stats.ytVideoViews, ytPosts, stats.ytVideoId, stats.ytPostedAt, stats.ytRate,
                            display.ytVsAvg, display.ytSpeed, compact = true,
                        ),
                        if (display.ytTitle) stats.ytVideoTitle else "",
                    ),
                    error = stats.ytError,
                )
                Spacer(GlanceModifier.width(8.dp))
                Panel(
                    modifier = GlanceModifier.defaultWeight(),
                    label = "Facebook",
                    accent = FbBlue,
                    iconRes = R.drawable.ic_facebook,
                    cardRes = R.drawable.card_fb,
                    configured = configured.fb,
                    big = stats.fbFollowers,
                    bigLabel = "followers",
                    growth = if (display.fbGrowth) growth.fb else null,
                    metrics = fbMetrics(stats, display),
                    title = widgetLine(
                        if (display.fbPosted) postedAgo(stats.fbPostedAt).removePrefix("Posted ") else "",
                        insightLine(
                            stats.fbVideoViews, fbPosts, stats.fbVideoId, stats.fbPostedAt, stats.fbRate,
                            display.fbVsAvg, display.fbSpeed, compact = true,
                        ),
                        if (display.fbTitle && stats.fbVideoTitle.isNotBlank()) "Reel · " + stats.fbVideoTitle else "",
                    ),
                    error = stats.fbError,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            // The whole bottom strip refreshes, not just the small label: a tap that lands near the button
            // refreshes instead of opening the app. While a refresh runs the strip says so and ignores taps.
            val strip = if (refreshing) GlanceModifier.fillMaxWidth()
            else GlanceModifier.fillMaxWidth().clickable(actionRunCallback<RefreshAction>())
            Row(
                modifier = strip.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                val time = when {
                    refreshing -> "Updating your numbers…"
                    stats.updatedAt > 0 ->
                        "Updated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(stats.updatedAt))
                    else -> "Not refreshed yet"
                }
                Text(
                    text = time,
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                )
                Row(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.btn_refresh))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier.size(11.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(if (refreshing) TextDim else TextMain)),
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        text = if (refreshing) "Working…" else "Refresh",
                        style = TextStyle(
                            color = ColorProvider(if (refreshing) TextDim else TextMain),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Panel(
    modifier: GlanceModifier,
    label: String,
    accent: Color,
    iconRes: Int,
    cardRes: Int,
    configured: Boolean,
    big: Long,
    bigLabel: String,
    growth: Long?,
    metrics: List<Metric>,
    title: String,
    error: String,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ImageProvider(cardRes))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (!configured) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(15.dp),
                )
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = label,
                    style = TextStyle(color = ColorProvider(accent), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Not set up",
                style = TextStyle(color = ColorProvider(TextMain), fontSize = 13.sp),
            )
            Text(
                text = "Tap to open settings",
                style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
            )
        } else {
            // The brand icon sits beside the number (no separate name row), which leaves room for the title.
            Row(verticalAlignment = Alignment.Vertical.Bottom) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(18.dp).padding(bottom = 4.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = formatCount(big),
                    style = TextStyle(color = ColorProvider(TextMain), fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                // Today's change sits above the label, so it adds no height.
                val g = growthText(growth)
                Column(modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp)) {
                    if (g != null) {
                        Text(
                            text = g,
                            style = TextStyle(
                                color = ColorProvider(if ((growth ?: 0) > 0) GrowthUp else GrowthDown),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = bigLabel,
                        style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
            // Two small stats per row; the app lets you choose which ones appear.
            metrics.chunked(2).forEach { pair ->
                Spacer(GlanceModifier.height(3.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    pair.forEachIndexed { i, m ->
                        if (i > 0) Spacer(GlanceModifier.width(10.dp))
                        Image(
                            provider = ImageProvider(m.iconRes),
                            contentDescription = m.desc,
                            modifier = GlanceModifier.size(12.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(TextDim)),
                        )
                        Spacer(GlanceModifier.width(3.dp))
                        Text(
                            text = formatCount(m.value),
                            style = TextStyle(color = ColorProvider(TextMain), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (error.isNotBlank()) {
                Text(
                    text = "! $error",
                    style = TextStyle(color = ColorProvider(Warn), fontSize = 9.sp),
                    maxLines = 1,
                )
            } else if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Show "Updating…" at once so the tap visibly registered. Offline there is nothing to wait for.
        if (isOnline(context)) Store.setRefreshingAt(context, System.currentTimeMillis())
        Scheduler.refreshNow(context)
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Scheduler.schedule(context)
        Scheduler.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Scheduler.cancelIfUnused(context)
    }
}
