package com.abir.socialwidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
private val YtRed = Color(0xFFFF5A4D)
private val FbBlue = Color(0xFF5AA2FF)
private val Warn = Color(0xFFFFB454)

fun formatCount(n: Long): String = when {
    n < 0 -> "—"
    n >= 1_000_000_000 -> trim1(n / 1_000_000_000.0) + "B"
    n >= 1_000_000 -> trim1(n / 1_000_000.0) + "M"
    n >= 10_000 -> trim1(n / 1_000.0) + "K"
    else -> "%,d".format(Locale.US, n)
}

private fun trim1(d: Double): String {
    val s = String.format(Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

class StatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = Store.loadSettings(context)
        val stats = Store.loadStats(context)
        provideContent { Content(settings, stats) }
    }
}

@Composable
private fun Content(settings: Settings, stats: Stats) {
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
                    configured = settings.ytConfigured,
                    big = stats.ytSubs,
                    bigLabel = "subscribers",
                    views = stats.ytVideoViews,
                    comments = stats.ytComments,
                    title = stats.ytVideoTitle,
                    error = stats.ytError,
                )
                Spacer(GlanceModifier.width(8.dp))
                Panel(
                    modifier = GlanceModifier.defaultWeight(),
                    label = "Facebook",
                    accent = FbBlue,
                    iconRes = R.drawable.ic_facebook,
                    cardRes = R.drawable.card_fb,
                    configured = settings.fbConfigured,
                    big = stats.fbFollowers,
                    bigLabel = "followers",
                    views = stats.fbVideoViews,
                    comments = stats.fbComments,
                    title = stats.fbVideoTitle,
                    error = stats.fbError,
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                val time = if (stats.updatedAt > 0)
                    "Updated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(stats.updatedAt))
                else "Not refreshed yet"
                Text(
                    text = time,
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                )
                // Separate tap target: refresh now.
                Row(
                    modifier = GlanceModifier
                        .clickable(actionRunCallback<RefreshAction>())
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier.size(11.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(TextDim)),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                    Text(
                        text = "Refresh",
                        style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
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
    views: Long,
    comments: Long,
    title: String,
    error: String,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ImageProvider(cardRes))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
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
        if (!configured) {
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
            Row(verticalAlignment = Alignment.Vertical.Bottom) {
                Text(
                    text = formatCount(big),
                    style = TextStyle(color = ColorProvider(TextMain), fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = " $bigLabel",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(3.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_eye),
                    contentDescription = "Views",
                    modifier = GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(TextDim)),
                )
                Spacer(GlanceModifier.width(3.dp))
                Text(
                    text = formatCount(views),
                    style = TextStyle(color = ColorProvider(TextMain), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (comments >= 0) {
                    Spacer(GlanceModifier.width(10.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_comment),
                        contentDescription = "Comments",
                        modifier = GlanceModifier.size(11.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(TextDim)),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                    Text(
                        text = formatCount(comments),
                        style = TextStyle(color = ColorProvider(TextMain), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
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
        Scheduler.refreshNow(context)
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
        Scheduler.cancel(context)
    }
}
