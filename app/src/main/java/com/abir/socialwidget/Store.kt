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
    val ytError: String = "",
    val fbFollowers: Long = -1,
    val fbVideoViews: Long = -1,
    val fbVideoTitle: String = "",
    val fbComments: Long = -1,
    val fbVideoId: String = "",
    val fbReactions: Long = -1,
    val fbShares: Long = -1,
    val fbPostedAt: Long = 0,
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
            notifyMilestones = it.getBoolean("d_notifyMilestones", d.notifyMilestones),
            notifyFastRise = it.getBoolean("d_notifyFastRise", d.notifyFastRise),
            riseThreshold = it.getInt("d_riseThreshold", d.riseThreshold),
        )
    }

    fun saveDisplay(c: Context, d: Display) {
        sp(c).edit()
            .putBoolean("d_ytViews", d.ytViews)
            .putBoolean("d_ytComments", d.ytComments)
            .putBoolean("d_ytLikes", d.ytLikes)
            .putBoolean("d_ytTitle", d.ytTitle)
            .putBoolean("d_ytPosted", d.ytPosted)
            .putBoolean("d_fbPosted", d.fbPosted)
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
            ytError = it.getString("ytError", "").orEmpty(),
            fbFollowers = it.getLong("fbFollowers", -1),
            fbVideoViews = it.getLong("fbVideoViews", -1),
            fbVideoTitle = it.getString("fbVideoTitle", "").orEmpty(),
            fbComments = it.getLong("fbComments", -1),
            fbVideoId = it.getString("fbVideoId", "").orEmpty(),
            fbReactions = it.getLong("fbReactions", -1),
            fbShares = it.getLong("fbShares", -1),
            fbPostedAt = it.getLong("fbPostedAt", 0),
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
