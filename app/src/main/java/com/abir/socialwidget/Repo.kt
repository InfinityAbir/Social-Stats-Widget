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
                    ytChannelId = r.channelId, ytVideoId = r.videoId, ytLikes = r.likes,
                )
            } catch (e: Exception) {
                s.copy(ytError = e.message ?: "YouTube error")
            }
        } else {
            s = s.copy(
                ytSubs = -1, ytVideoViews = -1, ytVideoTitle = "", ytComments = -1,
                ytChannelId = "", ytVideoId = "", ytLikes = -1,
            )
        }

        if (settings.fbConfigured) {
            s = try {
                val r = withContext(Dispatchers.IO) { fetchFacebook(settings.fbPageId, settings.fbToken) }
                s.copy(
                    fbFollowers = r.followers, fbVideoViews = r.views, fbVideoTitle = r.title, fbComments = r.comments,
                    fbVideoId = r.videoId, fbReactions = r.reactions, fbShares = r.shares,
                )
            } catch (e: Exception) {
                s.copy(fbError = e.message ?: "Facebook error")
            }
        } else {
            s = s.copy(
                fbFollowers = -1, fbVideoViews = -1, fbVideoTitle = "", fbComments = -1,
                fbVideoId = "", fbReactions = -1, fbShares = -1,
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
    )

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
        return Yt(subs, views, title, comments, channelId, videoId, likes)
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
        try {
            // Use the reels edge: the plain /videos edge also returns Stories ("day" shares),
            // which would replace the last reel's numbers. Fall back to /videos only if the
            // reels edge errors (e.g. missing permission), not when it is simply empty.
            val fields = "fields=id,title,description,views,post_id&limit=1&access_token=$t"
            val vids = try {
                get("https://graph.facebook.com/$FB_VERSION/$id/video_reels?$fields")
            } catch (_: Exception) {
                get("https://graph.facebook.com/$FB_VERSION/$id/videos?$fields")
            }
            val data = vids.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val v = data.getJSONObject(0)
                views = v.optLong("views", -1)
                videoId = v.optString("id")
                postId = v.optString("post_id")
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
        return Fb(followers, views, title, comments, videoId, reactions, shares)
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
