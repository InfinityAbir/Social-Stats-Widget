package com.abir.socialwidget

/**
 * Choosing "the latest video/reel" in one place, for both platforms.
 *
 * Why this is not "first item of the list": YouTube's uploads playlist and Facebook's reels edge
 * are ordered by when an item was uploaded or added, not by when it went public. A video or reel
 * that was scheduled earlier but uploaded later sits above one that went live before it. A page
 * token / owner view also returns items that are scheduled but not public yet. So: keep only items
 * that are really live, then take the one with the newest go-live time.
 */
data class Candidate(
    val id: String,
    /** When it went public, epoch millis. */
    val publishedAt: Long,
    /** Public right now (not scheduled, private, upcoming or failed). */
    val live: Boolean,
)

/** Allowed gap between the phone clock and the server clock. */
const val CLOCK_SLACK_MS = 5 * 60_000L

/**
 * The live candidate with the newest [Candidate.publishedAt]. On a tie the earlier one in the list
 * wins. A "live" item stamped more than [CLOCK_SLACK_MS] in the future is not live yet.
 */
fun pickLatest(items: List<Candidate>, now: Long): Candidate? {
    var best: Candidate? = null
    for (c in items) {
        if (!c.live) continue
        if (c.publishedAt > now + CLOCK_SLACK_MS) continue
        if (best == null || c.publishedAt > best.publishedAt) best = c
    }
    return best
}

/**
 * Is this Facebook reel public? null means Meta gave no usable status, so the caller should ask
 * the reel's post instead. Meta's fields, strongest first:
 *  - `published`: false means scheduled or unpublished.
 *  - `status.publishing_phase.publish_status`: "published", "scheduled" or "not_published".
 *  - `scheduled_publish_time`: a future time means not live yet.
 *  - `status.video_status`: "ready" once processed. A reel that Meta already calls published may
 *    still be "processing" for a minute and must not be skipped for that.
 */
fun fbLiveness(
    published: Boolean?,
    publishStatus: String,
    videoStatus: String,
    scheduledAtMs: Long,
    now: Long,
): Boolean? {
    val bad = videoStatus.equals("error", true) || videoStatus.equals("expired", true)
    if (published == false) return false
    if (publishStatus.isNotEmpty()) return publishStatus.equals("published", true) && !bad
    if (scheduledAtMs > now) return false
    if (published == true) return !bad
    if (videoStatus.isNotEmpty()) {
        return videoStatus.equals("ready", true) || videoStatus.equals("published", true)
    }
    return null
}

/**
 * Is this YouTube video public? Unlisted and private videos are not "posted", a premiere that has
 * not started is "upcoming", and a failed or rejected upload never goes live.
 */
fun ytLiveness(privacyStatus: String, uploadStatus: String, liveBroadcastContent: String): Boolean {
    if (!privacyStatus.equals("public", true)) return false
    if (liveBroadcastContent.equals("upcoming", true)) return false
    return uploadStatus.lowercase() !in setOf("failed", "rejected", "deleted")
}
