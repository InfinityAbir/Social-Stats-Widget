package com.abir.socialwidget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Repo {
    private const val FB_VERSION = "v21.0"

    /** Fetch both platforms (each independently), cache result, redraw widgets. Never throws. */
    suspend fun refreshAll(context: Context): Stats {
        val app = context.applicationContext
        val settings = Store.loadSettings(app)
        val old = Store.loadStats(app)

        var s = old.copy(ytError = "", fbError = "")

        if (settings.ytConfigured) {
            s = try {
                val r = withContext(Dispatchers.IO) { fetchYoutube(settings.ytKey, settings.ytChannel) }
                s.copy(ytSubs = r.subs, ytVideoViews = r.views, ytVideoTitle = r.title, ytComments = r.comments)
            } catch (e: Exception) {
                s.copy(ytError = e.message ?: "YouTube error")
            }
        } else {
            s = s.copy(ytSubs = -1, ytVideoViews = -1, ytVideoTitle = "", ytComments = -1)
        }

        if (settings.fbConfigured) {
            s = try {
                val r = withContext(Dispatchers.IO) { fetchFacebook(settings.fbPageId, settings.fbToken) }
                s.copy(fbFollowers = r.followers, fbVideoViews = r.views, fbVideoTitle = r.title, fbComments = r.comments)
            } catch (e: Exception) {
                s.copy(fbError = e.message ?: "Facebook error")
            }
        } else {
            s = s.copy(fbFollowers = -1, fbVideoViews = -1, fbVideoTitle = "", fbComments = -1)
        }

        s = s.copy(updatedAt = System.currentTimeMillis())
        Store.saveStats(app, s)
        StatsWidget().updateAll(app)
        return s
    }

    // ---------- YouTube ----------

    private class Yt(val subs: Long, val views: Long, val title: String, val comments: Long = -1)

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

        val stats = item.getJSONObject("statistics")
        val subs = if (stats.optBoolean("hiddenSubscriberCount", false)) -1L
        else stats.optString("subscriberCount", "-1").toLongOrNull() ?: -1L

        val uploads = item.getJSONObject("contentDetails")
            .getJSONObject("relatedPlaylists").optString("uploads")
        if (uploads.isBlank()) return Yt(subs, -1, "")

        val pl = get(
            "https://www.googleapis.com/youtube/v3/playlistItems" +
                "?part=contentDetails&playlistId=${enc(uploads)}&maxResults=1&key=$k"
        )
        val plItems = pl.optJSONArray("items")
        if (plItems == null || plItems.length() == 0) return Yt(subs, -1, "")
        val videoId = plItems.getJSONObject(0).getJSONObject("contentDetails").getString("videoId")

        val vid = get(
            "https://www.googleapis.com/youtube/v3/videos" +
                "?part=statistics,snippet&id=${enc(videoId)}&key=$k"
        )
        val vItems = vid.optJSONArray("items")
        if (vItems == null || vItems.length() == 0) return Yt(subs, -1, "")
        val v = vItems.getJSONObject(0)
        val views = v.getJSONObject("statistics").optString("viewCount", "-1").toLongOrNull() ?: -1L
        val title = v.getJSONObject("snippet").optString("title")
        val comments = v.getJSONObject("statistics").optString("commentCount", "-1").toLongOrNull() ?: -1L
        return Yt(subs, views, title, comments)
    }

    // ---------- Facebook ----------

    private class Fb(val followers: Long, val views: Long, val title: String, val comments: Long = -1)

    private fun fetchFacebook(pageId: String, token: String): Fb {
        val t = enc(token.trim())
        val id = enc(pageId.trim())
        val page = get("https://graph.facebook.com/$FB_VERSION/$id?fields=followers_count,fan_count&access_token=$t")
        val followers = when {
            page.has("followers_count") -> page.optLong("followers_count", -1)
            page.has("fan_count") -> page.optLong("fan_count", -1)
            else -> -1L
        }

        // Last video views is best-effort: depends on Meta permissions, so failure is not fatal.
        var views = -1L
        var title = ""
        var videoId = ""
        var comments = -1L
        try {
            val vids = get("https://graph.facebook.com/$FB_VERSION/$id/videos?fields=id,title,description,views&limit=1&access_token=$t")
            val data = vids.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val v = data.getJSONObject(0)
                views = v.optLong("views", -1)
                videoId = v.optString("id")
                title = v.optString("title").ifBlank { v.optString("description").lineSequence().firstOrNull().orEmpty() }
            }
        } catch (_: Exception) {
        }
        // Comment count of that video, also best-effort.
        if (videoId.isNotBlank()) {
            try {
                val c = get("https://graph.facebook.com/$FB_VERSION/${enc(videoId)}/comments?summary=true&limit=0&access_token=$t")
                comments = c.optJSONObject("summary")?.optLong("total_count", -1) ?: -1L
            } catch (_: Exception) {
            }
        }
        return Fb(followers, views, title, comments)
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
