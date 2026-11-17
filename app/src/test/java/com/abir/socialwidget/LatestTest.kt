package com.abir.socialwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun c(id: String, agoHours: Long, live: Boolean = true) =
        Candidate(id, now - agoHours * hour, live)

    // ---- pickLatest ----

    @Test fun empty_list_gives_nothing() = assertNull(pickLatest(emptyList(), now))

    @Test fun nothing_live_gives_nothing() =
        assertNull(pickLatest(listOf(c("a", 1, live = false), c("b", 2, live = false)), now))

    @Test fun picks_newest_go_live_not_first_in_list() {
        // The reported bug: list order is by upload, the older go-live comes first.
        val list = listOf(c("went-live-6h-ago", 6), c("went-live-38m-ago", 0))
        assertEquals("went-live-38m-ago", pickLatest(list, now)?.id)
    }

    @Test fun list_order_does_not_matter() {
        val items = listOf(c("a", 9), c("b", 1), c("c", 5))
        assertEquals("b", pickLatest(items, now)?.id)
        assertEquals("b", pickLatest(items.reversed(), now)?.id)
    }

    @Test fun skips_scheduled_even_if_newest() {
        val list = listOf(c("scheduled", 0, live = false), c("live", 4))
        assertEquals("live", pickLatest(list, now)?.id)
    }

    @Test fun skips_item_stamped_in_the_future() {
        val future = Candidate("future", now + 2 * hour, live = true)
        assertEquals("old", pickLatest(listOf(future, c("old", 3)), now)?.id)
    }

    @Test fun tolerates_small_clock_difference() {
        // Phone clock a minute behind the server: a reel that just went live must still count.
        val justNow = Candidate("just-now", now + 60_000, live = true)
        assertEquals("just-now", pickLatest(listOf(justNow, c("old", 3)), now)?.id)
    }

    @Test fun tie_keeps_first_in_list() =
        assertEquals("first", pickLatest(listOf(c("first", 2), c("second", 2)), now)?.id)

    // ---- fbLiveness ----

    private fun fb(
        published: Boolean? = null, publishStatus: String = "", videoStatus: String = "",
        scheduledAtMs: Long = 0,
    ) = fbLiveness(published, publishStatus, videoStatus, scheduledAtMs, now)

    @Test fun fb_published_flag_false_is_not_live() = assertEquals(false, fb(published = false, videoStatus = "ready"))
    @Test fun fb_publish_status_published_is_live() = assertEquals(true, fb(publishStatus = "published", videoStatus = "ready"))
    @Test fun fb_publish_status_scheduled_is_not_live() = assertEquals(false, fb(publishStatus = "scheduled", videoStatus = "ready"))
    @Test fun fb_publish_status_not_published_is_not_live() = assertEquals(false, fb(publishStatus = "not_published"))

    @Test fun fb_just_published_but_still_processing_is_live() {
        // Meta calls it published; processing must not hide it for the first minutes.
        assertEquals(true, fb(published = true, publishStatus = "published", videoStatus = "processing"))
        assertEquals(true, fb(published = true, videoStatus = "processing"))
    }

    @Test fun fb_future_schedule_time_is_not_live() = assertEquals(false, fb(scheduledAtMs = now + hour, videoStatus = "ready"))
    @Test fun fb_past_schedule_time_with_ready_is_live() = assertEquals(true, fb(scheduledAtMs = now - hour, videoStatus = "ready"))
    @Test fun fb_error_and_expired_are_not_live() {
        assertEquals(false, fb(published = true, videoStatus = "error"))
        assertEquals(false, fb(publishStatus = "published", videoStatus = "expired"))
    }

    @Test fun fb_ready_only_is_live() = assertEquals(true, fb(videoStatus = "ready"))
    @Test fun fb_processing_only_is_not_live_yet() = assertEquals(false, fb(videoStatus = "processing"))
    @Test fun fb_no_information_is_unknown() = assertNull(fb())

    // ---- ytLiveness ----

    @Test fun yt_public_processed_is_live() = assertTrue(ytLiveness("public", "processed", "none"))
    @Test fun yt_public_still_processing_is_live() = assertTrue(ytLiveness("public", "uploaded", "none"))
    @Test fun yt_private_unlisted_are_not_live() {
        assertFalse(ytLiveness("private", "processed", "none"))
        assertFalse(ytLiveness("unlisted", "processed", "none"))
    }
    @Test fun yt_upcoming_premiere_is_not_live() = assertFalse(ytLiveness("public", "processed", "upcoming"))
    @Test fun yt_running_livestream_is_live() = assertTrue(ytLiveness("public", "processed", "live"))
    @Test fun yt_failed_or_rejected_is_not_live() {
        assertFalse(ytLiveness("public", "failed", "none"))
        assertFalse(ytLiveness("public", "rejected", "none"))
    }
}
