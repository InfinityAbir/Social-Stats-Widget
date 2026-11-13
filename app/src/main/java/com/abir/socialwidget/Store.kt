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
    val ytError: String = "",
    val fbFollowers: Long = -1,
    val fbVideoViews: Long = -1,
    val fbVideoTitle: String = "",
    val fbComments: Long = -1,
    val fbError: String = "",
    val updatedAt: Long = 0,
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("socialwidget", Context.MODE_PRIVATE)

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
            ytError = it.getString("ytError", "").orEmpty(),
            fbFollowers = it.getLong("fbFollowers", -1),
            fbVideoViews = it.getLong("fbVideoViews", -1),
            fbVideoTitle = it.getString("fbVideoTitle", "").orEmpty(),
            fbComments = it.getLong("fbComments", -1),
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
            .putString("ytError", s.ytError)
            .putLong("fbFollowers", s.fbFollowers)
            .putLong("fbVideoViews", s.fbVideoViews)
            .putString("fbVideoTitle", s.fbVideoTitle)
            .putLong("fbComments", s.fbComments)
            .putString("fbError", s.fbError)
            .putLong("updatedAt", s.updatedAt)
            .apply()
    }
}
