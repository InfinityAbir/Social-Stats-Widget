package com.abir.socialwidget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Repo {
    private const val FB_VERSION = "v21.0"

    /** Fetch both platforms (each independently), cache result, redraw widgets. Never throws. */
    suspend fun refreshAll(context: Context): Stats = withContext(NonCancellable) { refreshAllNow(context) }

    // NonCancellable: leaving the app mid-refresh must not drop the save and the widget redraw.
    private suspend fun refreshAllNow(context: Context): Stats {
        val app = context.applicationContext
        val settings = Store.loadSettings(app)
        val old = Store.loadStats(app)

        var s = old.copy(ytError = "", fbError = "")

        if (settings.ytConfigured) {
            s = try {
                val r = withContext(Dispatchers.IO) { fetchYoutube(settings.ytKey, settings.ytChannel) }
                s.copy(
                    ytSubs = r.subs, ytVideoViews = r.views, ytVideoTitle = r.title, ytComments = r.comments,
                    ytChannelId = r.channelId, ytVideoId = r.videoId, ytLikes = r.likes, ytPostedAt = r.postedAt,
                )
            } catch (e: Exception) {
                s.copy(ytError = e.message ?: "YouTube error")
            }
        } else {
            s = s.copy(
                ytSubs = -1, ytVideoViews = -1, ytVideoTitle = "", ytComments = -1,
                ytChannelId = "", ytVideoId = "", ytLikes = -1, ytPostedAt = 0,
            )
        }

        if (settings.fbConfigured) {
            s = try {
                val r = withContext(Dispatchers.IO) { fetchFacebook(settings.fbPageId, settings.fbToken) }
                s.copy(
                    fbFollowers = r.followers, fbVideoViews = r.views, fbVideoTitle = r.title, fbComments = r.comments,
                    fbVideoId = r.videoId, fbReactions = r.reactions, fbShares = r.shares, fbPostedAt = r.postedAt,
                )
            } catch (e: Exception) {
                s.copy(fbError = e.message ?: "Facebook error")
            }
        } else {
            s = s.copy(
                fbFollowers = -1, fbVideoViews = -1, fbVideoTitle = "", fbComments = -1,
                fbVideoId = "", fbReactions = -1, fbShares = -1, fbPostedAt = 0,
            )
        }

        s = s.copy(updatedAt = System.currentTimeMillis())
        Store.saveStats(app, s)
        History.record(app, s)
        runCatching { Notifier.check(app, s, Store.loadDisplay(app)) }
        redrawWidgets(app)
        return s
    }

    /** Redraw every placed widget from the cached numbers (also used after the display choices change). */
    suspend fun redrawWidgets(context: Context) {
        val app = context.applicationContext
        StatsWidget().updateAll(app)
        CompactStatsWidget().updateAll(app)
    }

    // ---------- YouTube ----------

    private class Yt(
        val subs: Long,
        val views: Long,
        val title: String,
        val comments: Long = -1,
        val channelId: String = "",
        val videoId: String = "",
        val likes: Long = -1,
        val postedAt: Long = 0,
    )

    /** Parses Meta/YouTube ISO timestamps ("...Z" or "...+0000") to epoch millis; 0 if unparseable. */
    private fun parseTime(s: String): Long {
        if (s.isBlank()) return 0
        return runCatching {
            val utc = s.endsWith("Z")
            val fmt = java.text.SimpleDateFormat(
                if (utc) "yyyy-MM-dd'T'HH:mm:ss'Z'" else "yyyy-MM-dd'T'HH:mm:ssZ",
                java.util.Locale.US,
            )
            if (utc) fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.parse(s)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun fetchYoutube(key: String, channelInput: String): Yt {
        val k = enc(key.trim())
        val ch = channelInput.trim().trimEnd('/').substringAfterLast('/')
        val selector = if (ch.startsWith("UC") && ch.length >= 20) {
            "id=${enc(ch)}"
        } else {
            "forHandle=${enc(if (ch.startsWith("@")) ch else "@$ch")}"
        }

        val channel = get(
            "https://www.googleapis.com/youtube/v3/channels" +
                "?part=statistics,contentDetails&$selector&key=$k"
        )
        val items = channel.optJSONArray("items")
        if (items == null || items.length() == 0) throw IOException("YouTube channel not found")
        val item = items.getJSONObject(0)
        val channelId = item.optString("id")

        val stats = item.getJSONObject("statistics")
        val subs = if (stats.optBoolean("hiddenSubscriberCount", false)) -1L
        else stats.optString("subscriberCount", "-1").toLongOrNull() ?: -1L

        val uploads = item.getJSONObject("contentDetails")
            .getJSONObject("relatedPlaylists").optString("uploads")
        if (uploads.isBlank()) return Yt(subs, -1, "", channelId = channelId)

        val pl = get(
            "https://www.googleapis.com/youtube/v3/playlistItems" +
                "?part=contentDetails&playlistId=${enc(uploads)}&maxResults=1&key=$k"
        )
        val plItems = pl.optJSONArray("items")
        if (plItems == null || plItems.length() == 0) return Yt(subs, -1, "", channelId = channelId)
        val videoId = plItems.getJSONObject(0).getJSONObject("contentDetails").getString("videoId")

        val vid = get(
            "https://www.googleapis.com/youtube/v3/videos" +
                "?part=statistics,snippet&id=${enc(videoId)}&key=$k"
        )
        val vItems = vid.optJSONArray("items")
        if (vItems == null || vItems.length() == 0) return Yt(subs, -1, "", channelId = channelId)
        val v = vItems.getJSONObject(0)
        val views = v.getJSONObject("statistics").optString("viewCount", "-1").toLongOrNull() ?: -1L
        val title = v.getJSONObject("snippet").optString("title")
        val comments = v.getJSONObject("statistics").optString("commentCount", "-1").toLongOrNull() ?: -1L
        val likes = v.getJSONObject("statistics").optString("likeCount", "-1").toLongOrNull() ?: -1L
        val postedAt = parseTime(v.getJSONObject("snippet").optString("publishedAt"))
        return Yt(subs, views, title, comments, channelId, videoId, likes, postedAt)
    }

    // ---------- Facebook ----------

    private class Fb(
        val followers: Long,
        val views: Long,
        val title: String,
        val comments: Long = -1,
        val videoId: String = "",
        val reactions: Long = -1,
        val shares: Long = -1,
        val postedAt: Long = 0,
    )

    private fun fetchFacebook(pageId: String, token: String): Fb {
        val t = enc(token.trim())
        val id = enc(pageId.trim())
        val page = get("https://graph.facebook.com/$FB_VERSION/$id?fields=followers_count,fan_count&access_token=$t")
        val followers = when {
            page.has("followers_count") -> page.optLong("followers_count", -1)
            page.has("fan_count") -> page.optLong("fan_count", -1)
            else -> -1L
        }

        // Last reel views is best-effort: depends on Meta permissions, so failure is not fatal.
        var views = -1L
        var title = ""
        var videoId = ""
        var comments = -1L
        var postId = ""
        var postedAt = 0L
        try {
            // Use the reels edge: the plain /videos edge also returns Stories ("day" shares),
            // which would replace the last reel's numbers. Fall back to /videos only if the
            // reels edge errors (e.g. missing permission), not when it is simply empty.
            //
            // A page token also sees reels that are scheduled but not live yet. They come back
            // first (newest upload), so pull a few and skip anything not published.
            val base = "id,title,description,views,post_id,created_time"
            val extra = "$base,published,status,scheduled_publish_time"
            fun load(fields: String): JSONObject = try {
                get("https://graph.facebook.com/$FB_VERSION/$id/video_reels?fields=$fields&limit=10&access_token=$t")
            } catch (_: Exception) {
                get("https://graph.facebook.com/$FB_VERSION/$id/videos?fields=$fields&limit=10&access_token=$t")
            }
            val vids = try {
                load(extra)
            } catch (_: Exception) {
                load(base) // a status field the API rejects must not break the whole lookup
            }
            val data = vids.optJSONArray("data")
            var picked: JSONObject? = null
            if (data != null) {
                for (i in 0 until data.length()) {
                    val cand = data.getJSONObject(i)
                    if (isLiveFbVideo(cand, id, t)) {
                        picked = cand
                        break
                    }
                }
            }
            if (picked != null) {
                val v = picked
                views = v.optLong("views", -1)
                videoId = v.optString("id")
                postId = v.optString("post_id")
                // `created_time` is the upload time, hours earlier for a reel that was scheduled.
                // The real go-live time is status.publishing_phase.publish_time.
                val live = parseTime(
                    v.optJSONObject("status")?.optJSONObject("publishing_phase")?.optString("publish_time").orEmpty()
                )
                val sched = v.optLong("scheduled_publish_time", 0) * 1000
                postedAt = when {
                    live > 0 -> live
                    sched > 0 -> sched
                    else -> parseTime(v.optString("created_time"))
                }
                title = v.optString("title").ifBlank { v.optString("description").lineSequence().firstOrNull().orEmpty() }
            }
        } catch (_: Exception) {
        }
        // Comment count of that video, also best-effort.
        var reactions = -1L
        var shares = -1L
        if (videoId.isNotBlank()) {
            val vid = enc(videoId)
            try {
                val c = get("https://graph.facebook.com/$FB_VERSION/$vid/comments?summary=true&limit=0&access_token=$t")
                comments = c.optJSONObject("summary")?.optLong("total_count", -1) ?: -1L
            } catch (_: Exception) {
            }
            // Reactions and shares are best-effort too. A reel node has no `reactions` edge, but its
            // `likes` edge carries the total reaction count.
            try {
                val r = get("https://graph.facebook.com/$FB_VERSION/$vid/likes?summary=true&limit=0&access_token=$t")
                reactions = r.optJSONObject("summary")?.optLong("total_count", -1) ?: -1L
            } catch (_: Exception) {
            }
            // Shares live on the reel's post, which Meta only serves with the pages_read_user_content
            // permission. Without it this stays hidden.
            if (postId.isNotBlank()) {
                try {
                    val post = if (postId.contains('_')) postId else "${pageId.trim()}_$postId"
                    val sh = get("https://graph.facebook.com/$FB_VERSION/${enc(post)}?fields=shares&access_token=$t")
                    shares = sh.optJSONObject("shares")?.optLong("count", 0) ?: 0L
                } catch (_: Exception) {
                }
            }
        }
        return Fb(followers, views, title, comments, videoId, reactions, shares, postedAt)
    }

    /**
     * False for a reel/video that is scheduled or otherwise not public yet. [pageEnc] and [tokenEnc]
     * are already URL-encoded. Checks the status fields when Meta returned them, otherwise asks the
     * reel's post for `is_published`. Unknown means live, so a missing permission never hides a reel.
     */
    private fun isLiveFbVideo(v: JSONObject, pageEnc: String, tokenEnc: String): Boolean {
        if (v.has("published") && !v.optBoolean("published", true)) return false
        val scheduledAt = v.optLong("scheduled_publish_time", 0)
        if (scheduledAt > System.currentTimeMillis() / 1000) return false
        val state = v.optJSONObject("status")?.optString("video_status").orEmpty()
        if (state.isNotEmpty()) return state.equals("ready", ignoreCase = true) ||
            state.equals("published", ignoreCase = true)
        // No status data: fall back to the post.
        val postId = v.optString("post_id")
        if (postId.isBlank()) return true
        return try {
            val post = if (postId.contains('_')) postId else "${pageEnc}_$postId"
            val p = get("https://graph.facebook.com/$FB_VERSION/${enc(post)}?fields=is_published&access_token=$tokenEnc")
            p.optBoolean("is_published", true)
        } catch (_: Exception) {
            true
        }
    }

    // ---------- HTTP ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.requestMethod = "GET"
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }
                    .getOrDefault("HTTP $code")
                throw IOException(msg)
            }
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }
}
