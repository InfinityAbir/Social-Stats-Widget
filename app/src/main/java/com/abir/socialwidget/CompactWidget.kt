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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val CompactMain = Color(0xFFFFFFFF)

/** A 2x1 strip: subscribers and followers with today's change. Tap to open the app. */
class CompactStatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Re-read on every saved change; see prefsTicks for why.
            val tick by prefsTicks(context).collectAsState(0L)
            val configured = remember(tick) { Store.loadConfigured(context) }
            val stats = remember(tick) { Store.loadStats(context) }
            val display = remember(tick) { Store.loadDisplay(context) }
            val growth = remember(tick) { History.growth(context, stats) }
            CompactContent(configured, stats, display, growth)
        }
    }
}

@Composable
private fun CompactContent(settings: Configured, stats: Stats, display: Display, growth: Growth) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        CompactRow(
            iconRes = R.drawable.ic_youtube,
            name = "YouTube",
            configured = settings.yt,
            value = stats.ytSubs,
            growth = if (display.ytGrowth) growth.yt else null,
        )
        Spacer(GlanceModifier.size(4.dp))
        CompactRow(
            iconRes = R.drawable.ic_facebook,
            name = "Facebook",
            configured = settings.fb,
            value = stats.fbFollowers,
            growth = if (display.fbGrowth) growth.fb else null,
        )
    }
}

@Composable
private fun CompactRow(iconRes: Int, name: String, configured: Boolean, value: Long, growth: Long?) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = name,
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = if (configured) formatCount(value) else "Not set up",
            style = TextStyle(color = ColorProvider(CompactMain), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        val g = if (configured) growthText(growth) else null
        if (g != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = g.removeSuffix(" today"),
                style = TextStyle(
                    color = ColorProvider(if ((growth ?: 0) > 0) GrowthUp else GrowthDown),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactStatsWidget()

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
