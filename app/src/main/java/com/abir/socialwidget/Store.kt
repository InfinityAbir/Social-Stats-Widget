package com.abir.socialwidget

import android.content.Context

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

    fun loadSettings(c: Context): Settings = sp(c).let {
        Settings(
            ytKey = it.getString("ytKey", "").orEmpty(),
            ytChannel = it.getString("ytChannel", "").orEmpty(),
            fbPageId = it.getString("fbPageId", "").orEmpty(),
            fbToken = it.getString("fbToken", "").orEmpty(),
        )
    }

    fun saveSettings(c: Context, s: Settings) {
        sp(c).edit()
            .putString("ytKey", s.ytKey.trim())
            .putString("ytChannel", s.ytChannel.trim())
            .putString("fbPageId", s.fbPageId.trim())
            .putString("fbToken", s.fbToken.trim())
            .apply()
    }

    fun loadStats(c: Context): Stats = sp(c).let {
        Stats(
            ytSubs = it.getLong("ytSubs", -1),
            ytVideoViews = it.getLong("ytVideoViews", -1),
            ytVideoTitle = it.getString("ytVideoTitle", "").orEmpty(),
            ytComments = it.getLong("ytComments", -1),
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
