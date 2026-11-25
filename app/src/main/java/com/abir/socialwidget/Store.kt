package com.abir.socialwidget

import android.content.Context
import android.content.SharedPreferences

data class Settings(
    val ytKey: String = "",
    val ytChannel: String = "",
    val fbPageId: String = "",
    val fbToken: String = "",
) {
    val ytConfigured get() = ytKey.isNotBlank() && ytChannel.isNotBlank()
    val fbConfigured get() = fbPageId.isNotBlank() && fbToken.isNotBlank()
}

/** -1 means "unknown". */
data class Stats(
    val ytSubs: Long = -1,
    val ytVideoViews: Long = -1,
    val ytVideoTitle: String = "",
    val ytComments: Long = -1,
    val ytChannelId: String = "",
    val ytVideoId: String = "",
    val ytLikes: Long = -1,
    /** When the last video went public, epoch millis; 0 means unknown. */
    val ytPostedAt: Long = 0,
    /** Views gained per hour by the latest video right now; -1 unknown. */
    val ytRate: Long = -1,
    val ytError: String = "",
    val fbFollowers: Long = -1,
    val fbVideoViews: Long = -1,
    val fbVideoTitle: String = "",
    val fbComments: Long = -1,
    val fbVideoId: String = "",
    val fbReactions: Long = -1,
    val fbShares: Long = -1,
    val fbPostedAt: Long = 0,
    val fbRate: Long = -1,
    val fbError: String = "",
    val updatedAt: Long = 0,
)

object Store {
    internal fun sp(c: Context) = c.getSharedPreferences("socialwidget", Context.MODE_PRIVATE)

    // ---------- Display and alert preferences ----------

    fun loadDisplay(c: Context): Display = sp(c).let {
        val d = Display()
        Display(
            ytViews = it.getBoolean("d_ytViews", d.ytViews),
            ytComments = it.getBoolean("d_ytComments", d.ytComments),
            ytLikes = it.getBoolean("d_ytLikes", d.ytLikes),
            ytTitle = it.getBoolean("d_ytTitle", d.ytTitle),
            ytPosted = it.getBoolean("d_ytPosted", d.ytPosted),
            ytGrowth = it.getBoolean("d_ytGrowth", d.ytGrowth),
            fbViews = it.getBoolean("d_fbViews", d.fbViews),
            fbComments = it.getBoolean("d_fbComments", d.fbComments),
            fbReactions = it.getBoolean("d_fbReactions", d.fbReactions),
            fbShares = it.getBoolean("d_fbShares", d.fbShares),
            fbTitle = it.getBoolean("d_fbTitle", d.fbTitle),
            fbPosted = it.getBoolean("d_fbPosted", d.fbPosted),
            fbGrowth = it.getBoolean("d_fbGrowth", d.fbGrowth),
            ytVsAvg = it.getBoolean("d_ytVsAvg", d.ytVsAvg),
            ytSpeed = it.getBoolean("d_ytSpeed", d.ytSpeed),
            fbVsAvg = it.getBoolean("d_fbVsAvg", d.fbVsAvg),
            fbSpeed = it.getBoolean("d_fbSpeed", d.fbSpeed),
            notifyMilestones = it.getBoolean("d_notifyMilestones", d.notifyMilestones),
            notifyFastRise = it.getBoolean("d_notifyFastRise", d.notifyFastRise),
            riseThreshold = it.getInt("d_riseThreshold", d.riseThreshold),
            notifyComments = it.getBoolean("d_notifyComments", d.notifyComments),
            notifyStale = it.getBoolean("d_notifyStale", d.notifyStale),
            notifyReminder = it.getBoolean("d_notifyReminder", d.notifyReminder),
            reminderTimes = decodeTimes(it.getString("d_reminderTimes", "").orEmpty()),
            reminderDays = it.getInt("d_reminderDays", d.reminderDays),
            reminderLead = it.getInt("d_reminderLead", d.reminderLead),
        )
    }

    // ---------- Refresh in progress (for the widget) ----------

    /** When the current refresh started, or 0 when none is running. */
    fun refreshingAt(c: Context): Long = sp(c).getLong("refreshing_at", 0)

    fun setRefreshingAt(c: Context, at: Long) {
        sp(c).edit().putLong("refreshing_at", at).apply()
    }

    // ---------- Appearance ----------

    fun themeMode(c: Context): ThemeMode =
        ThemeMode.entries.getOrElse(sp(c).getInt("theme_mode", 0)) { ThemeMode.SYSTEM }

    fun setThemeMode(c: Context, mode: ThemeMode) {
        sp(c).edit().putInt("theme_mode", mode.ordinal).apply()
    }

    // ---------- App lock ----------

    /** Whether opening the app asks for fingerprint, face or screen lock. On unless the user turns it off. */
    fun lockEnabled(c: Context): Boolean = sp(c).getBoolean("app_lock", true)

    fun setLockEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("app_lock", on).apply()
    }

    // ---------- Recent posts (for averages, best post, best time) ----------

    fun loadPosts(c: Context, platform: String): List<Post> {
        val raw = sp(c).getString("posts_$platform", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Post(o.getString("i"), o.optString("t"), o.optLong("v", -1), o.optLong("p", 0))
            }
        }.getOrDefault(emptyList())
    }

    fun savePosts(c: Context, platform: String, posts: List<Post>) {
        val arr = org.json.JSONArray()
        posts.forEach {
            arr.put(org.json.JSONObject().put("i", it.id).put("t", it.title.take(80)).put("v", it.views).put("p", it.publishedAt))
        }
        sp(c).edit().putString("posts_$platform", arr.toString()).apply()
    }

    // ---------- Speed baseline ----------

    fun loadSample(c: Context, platform: String): Sample? = sp(c).let {
        val id = it.getString("vel_${platform}_id", "").orEmpty()
        if (id.isBlank()) null else Sample(id, it.getLong("vel_${platform}_t", 0), it.getLong("vel_${platform}_v", -1))
    }

    fun saveSample(c: Context, platform: String, s: Sample?) {
        val e = sp(c).edit()
        if (s == null) e.remove("vel_${platform}_id").remove("vel_${platform}_t").remove("vel_${platform}_v")
        else e.putString("vel_${platform}_id", s.id).putLong("vel_${platform}_t", s.t).putLong("vel_${platform}_v", s.views)
        e.apply()
    }

    fun saveDisplay(c: Context, d: Display) {
        sp(c).edit()
            .putBoolean("d_ytViews", d.ytViews)
            .putBoolean("d_ytComments", d.ytComments)
            .putBoolean("d_ytLikes", d.ytLikes)
            .putBoolean("d_ytTitle", d.ytTitle)
            .putBoolean("d_ytPosted", d.ytPosted)
            .putBoolean("d_fbPosted", d.fbPosted)
            .putBoolean("d_ytVsAvg", d.ytVsAvg)
            .putBoolean("d_ytSpeed", d.ytSpeed)
            .putBoolean("d_fbVsAvg", d.fbVsAvg)
            .putBoolean("d_fbSpeed", d.fbSpeed)
            .putBoolean("d_notifyComments", d.notifyComments)
            .putBoolean("d_notifyStale", d.notifyStale)
            .putBoolean("d_notifyReminder", d.notifyReminder)
            .putString("d_reminderTimes", encodeTimes(d.reminderTimes))
            .putInt("d_reminderDays", d.reminderDays)
            .putInt("d_reminderLead", d.reminderLead)
            .putBoolean("d_ytGrowth", d.ytGrowth)
            .putBoolean("d_fbViews", d.fbViews)
            .putBoolean("d_fbComments", d.fbComments)
            .putBoolean("d_fbReactions", d.fbReactions)
            .putBoolean("d_fbShares", d.fbShares)
            .putBoolean("d_fbTitle", d.fbTitle)
            .putBoolean("d_fbGrowth", d.fbGrowth)
            .putBoolean("d_notifyMilestones", d.notifyMilestones)
            .putBoolean("d_notifyFastRise", d.notifyFastRise)
            .putInt("d_riseThreshold", d.riseThreshold)
            .apply()
    }

    // The API key and Page token are stored encrypted (see Vault) under "<name>_enc".
    // The channel and Page ID are public identifiers and stay as plain text.

    @Synchronized
    fun loadSettings(c: Context): Settings {
        val p = sp(c)
        return Settings(
            ytKey = readSecret(p, "ytKey"),
            ytChannel = p.getString("ytChannel", "").orEmpty(),
            fbPageId = p.getString("fbPageId", "").orEmpty(),
            fbToken = readSecret(p, "fbToken"),
        )
    }

    /** Returns false if a secret could not be encrypted on this device; in that case the old value is kept. */
    @Synchronized
    fun saveSettings(c: Context, s: Settings): Boolean {
        val edit = sp(c).edit()
        var ok = writeSecret(edit, "ytKey", s.ytKey.trim())
        ok = writeSecret(edit, "fbToken", s.fbToken.trim()) && ok
        edit.putString("ytChannel", s.ytChannel.trim())
            .putString("fbPageId", s.fbPageId.trim())
            .apply()
        return ok
    }

    private fun readSecret(p: SharedPreferences, name: String): String {
        val sealed = p.getString("${name}_enc", null)
        if (sealed != null) return Vault.decrypt(sealed).orEmpty()
        // One-time migration: an older version stored this value as plain text.
        val legacy = p.getString(name, null)?.takeIf { it.isNotBlank() } ?: return ""
        val migrated = Vault.encrypt(legacy)
        if (migrated != null) p.edit().putString("${name}_enc", migrated).remove(name).apply()
        return legacy
    }

    private fun writeSecret(edit: SharedPreferences.Editor, name: String, value: String): Boolean {
        if (value.isBlank()) {
            edit.remove("${name}_enc").remove(name)
            return true
        }
        val sealed = Vault.encrypt(value) ?: return false
        edit.putString("${name}_enc", sealed).remove(name)
        return true
    }

    fun loadStats(c: Context): Stats = sp(c).let {
        Stats(
            ytSubs = it.getLong("ytSubs", -1),
            ytVideoViews = it.getLong("ytVideoViews", -1),
            ytVideoTitle = it.getString("ytVideoTitle", "").orEmpty(),
            ytComments = it.getLong("ytComments", -1),
            ytChannelId = it.getString("ytChannelId", "").orEmpty(),
            ytVideoId = it.getString("ytVideoId", "").orEmpty(),
            ytLikes = it.getLong("ytLikes", -1),
            ytPostedAt = it.getLong("ytPostedAt", 0),
            ytRate = it.getLong("ytRate", -1),
            ytError = it.getString("ytError", "").orEmpty(),
            fbFollowers = it.getLong("fbFollowers", -1),
            fbVideoViews = it.getLong("fbVideoViews", -1),
            fbVideoTitle = it.getString("fbVideoTitle", "").orEmpty(),
            fbComments = it.getLong("fbComments", -1),
            fbVideoId = it.getString("fbVideoId", "").orEmpty(),
            fbReactions = it.getLong("fbReactions", -1),
            fbShares = it.getLong("fbShares", -1),
            fbPostedAt = it.getLong("fbPostedAt", 0),
            fbRate = it.getLong("fbRate", -1),
            fbError = it.getString("fbError", "").orEmpty(),
            updatedAt = it.getLong("updatedAt", 0),
        )
    }

    fun saveStats(c: Context, s: Stats) {
        sp(c).edit()
            .putLong("ytSubs", s.ytSubs)
            .putLong("ytVideoViews", s.ytVideoViews)
            .putString("ytVideoTitle", s.ytVideoTitle)
            .putLong("ytComments", s.ytComments)
            .putString("ytChannelId", s.ytChannelId)
            .putString("ytVideoId", s.ytVideoId)
            .putLong("ytLikes", s.ytLikes)
            .putLong("ytPostedAt", s.ytPostedAt)
            .putLong("fbPostedAt", s.fbPostedAt)
            .putLong("ytRate", s.ytRate)
            .putLong("fbRate", s.fbRate)
            .putString("ytError", s.ytError)
            .putLong("fbFollowers", s.fbFollowers)
            .putLong("fbVideoViews", s.fbVideoViews)
            .putString("fbVideoTitle", s.fbVideoTitle)
            .putLong("fbComments", s.fbComments)
            .putString("fbVideoId", s.fbVideoId)
            .putLong("fbReactions", s.fbReactions)
            .putLong("fbShares", s.fbShares)
            .putString("fbError", s.fbError)
            .putLong("updatedAt", s.updatedAt)
            .apply()
    }
}
