package com.abir.socialwidget

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/**
 * Two optional alerts, both checked after every refresh:
 *  - milestones: a count passes 100, 200, 500, 1K, 2K, 5K, 10K, 20K, 50K ... (followers, subscribers, latest video views)
 *  - fast rise: the latest video gains at least N views per hour
 * The first time a number is seen it is only remembered, never announced.
 */
object Notifier {
    private const val CHANNEL = "milestones"
    private const val HOUR_MS = 3_600_000L
    private const val MIN_WINDOW_MS = 55 * 60_000L

    /** The largest milestone (1, 2 or 5 times a power of ten, from 100 up) that [n] has reached; 0 if none. */
    fun milestoneFloor(n: Long): Long {
        if (n < 100) return 0
        var best = 0L
        var base = 100L
        while (true) {
            for (m in longArrayOf(1, 2, 5)) {
                val v = base * m
                if (v > n) return best
                best = v
            }
            base *= 10
        }
    }

    fun check(c: Context, s: Stats, d: Display, now: Long = System.currentTimeMillis()) {
        if (!d.anyAlerts) return
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return
        if (d.notifyMilestones) {
            milestone(c, "ytSubs", "", s.ytSubs, "YouTube subscribers", null)
            milestone(c, "fbFollowers", "", s.fbFollowers, "Facebook followers", null)
            milestone(c, "ytViews", s.ytVideoId, s.ytVideoViews, "YouTube video views", s.ytVideoTitle)
            milestone(c, "fbViews", s.fbVideoId, s.fbVideoViews, "Facebook reel views", s.fbVideoTitle)
        }
        if (d.notifyFastRise) {
            rise(c, "yt", "YouTube video", s.ytVideoId, s.ytVideoViews, s.ytVideoTitle, d.riseThreshold, now)
            rise(c, "fb", "Facebook reel", s.fbVideoId, s.fbVideoViews, s.fbVideoTitle, d.riseThreshold, now)
        }
        if (d.notifyComments) {
            comments(c, "yt", s.ytVideoId, s.ytComments, "YouTube video", s.ytVideoTitle)
            comments(c, "fb", s.fbVideoId, s.fbComments, "Facebook reel", s.fbVideoTitle)
        }
        if (d.notifyStale) {
            // No internet is temporary and not the user's doing, so it never counts as a failure.
            stale(
                c, "yt", "YouTube", s.ytError.takeUnless(::isOfflineError).orEmpty(), now,
                "${s.ytError}. Check your API key and channel handle in the app.",
            )
            stale(
                c, "fb", "Facebook", s.fbError.takeUnless(::isOfflineError).orEmpty(), now,
                "${s.fbError}. Your Page token has probably expired: open the app and follow the renewal guide.",
            )
        }
    }

    /** A new comment on the latest video or reel. A first sighting or a different video says nothing. */
    private fun comments(c: Context, p: String, id: String, count: Long, what: String, title: String) {
        if (id.isBlank() || count < 0) return
        val sp = Store.sp(c)
        val prevId = sp.getString("cm_${p}_id", null)
        val prevCount = if (sp.contains("cm_${p}_n")) sp.getLong("cm_${p}_n", -1) else null
        val gained = newComments(prevId, prevCount, id, count)
        sp.edit().putString("cm_${p}_id", id).putLong("cm_${p}_n", count).apply()
        if (gained > 0) {
            val head = if (gained == 1L) "New comment" else "$gained new comments"
            post(
                c, "cm_$p".hashCode(),
                "$head on your $what",
                (if (title.isBlank()) "" else "\"$title\" ") + "now has ${"%,d".format(Locale.US, count)} comments.",
            )
        }
    }

    /** A connection that keeps failing (expired token, bad key). Once per episode, after a few hours. */
    private fun stale(c: Context, p: String, what: String, error: String, now: Long, text: String) {
        val sp = Store.sp(c)
        val step = staleStep(
            hasError = error.isNotBlank(),
            since = sp.getLong("st_${p}_since", 0),
            notified = sp.getBoolean("st_${p}_n", false),
            now = now,
        )
        sp.edit().putLong("st_${p}_since", step.since).putBoolean("st_${p}_n", step.notified).apply()
        if (step.fire) post(c, "stale_$p".hashCode(), "$what stopped updating", text)
    }

    /**
     * The "posting time is coming up" nudge, called by the reminder alarm. The title says how long is
     * really left, and nothing is posted if the posting time has already passed.
     */
    fun postReminder(c: Context, slotAt: Long, now: Long = System.currentTimeMillis()) {
        if (!shouldRemind(now, slotAt)) return
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return
        post(c, "reminder".hashCode(), reminderTitle(slotAt - now), reminderBody(minutesOfDay(slotAt)))
    }

    private fun milestone(c: Context, key: String, id: String, value: Long, what: String, title: String?) {
        if (value < 0) return
        val sp = Store.sp(c)
        val floor = milestoneFloor(value)
        val valKey = "ms_$key"
        val idKey = "ms_${key}_id"
        // First sighting, or a different video: remember the level, say nothing.
        if (!sp.contains(valKey) || sp.getString(idKey, "") != id) {
            sp.edit().putLong(valKey, floor).putString(idKey, id).apply()
            return
        }
        val seen = sp.getLong(valKey, 0)
        if (floor > seen) {
            sp.edit().putLong(valKey, floor).apply()
            val n = "%,d".format(Locale.US, floor)
            post(
                c, "ms_$key".hashCode(),
                "$what hit $n",
                if (title.isNullOrBlank()) "You just passed $n." else "\"$title\" just passed $n.",
            )
        }
    }

    private fun rise(
        c: Context, p: String, what: String, id: String, views: Long, title: String, perHour: Int, now: Long,
    ) {
        if (id.isBlank() || views < 0) return
        val sp = Store.sp(c)
        val savedId = sp.getString("rise_${p}_id", "")
        val t = sp.getLong("rise_${p}_t", 0)
        val v = sp.getLong("rise_${p}_v", 0)
        fun start() = sp.edit().putString("rise_${p}_id", id).putLong("rise_${p}_t", now).putLong("rise_${p}_v", views).apply()
        if (savedId != id || t == 0L) { start(); return }
        val elapsed = now - t
        if (elapsed < MIN_WINDOW_MS) return
        val gain = views - v
        start()
        if (gain <= 0) return
        val rate = gain * HOUR_MS / elapsed
        if (rate >= perHour) {
            val mins = (elapsed / 60_000L).coerceAtLeast(1)
            post(
                c, "rise_$p".hashCode(),
                "$what is taking off",
                (if (title.isBlank()) "" else "\"$title\" ") + "gained ${"%,d".format(Locale.US, gain)} views in the last $mins min.",
            )
        }
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked in check()
    private fun post(c: Context, id: Int, title: String, text: String) {
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(id, n) }
    }

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, c.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
