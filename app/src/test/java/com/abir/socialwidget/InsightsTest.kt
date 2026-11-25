package com.abir.socialwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class InsightsTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L
    private val utc = TimeZone.getTimeZone("UTC")

    private fun post(id: String, views: Long, ageHours: Long) = Post(id, "t$id", views, now - ageHours * hour)

    // ---- usualViews ----

    @Test fun average_excludes_the_latest_post() {
        val posts = listOf(post("latest", 9_999, 1), post("a", 100, 30), post("b", 200, 60), post("c", 300, 90))
        assertEquals(200L, usualViews(posts, "latest"))
    }

    @Test fun average_needs_three_older_posts() {
        val posts = listOf(post("latest", 500, 1), post("a", 100, 30), post("b", 200, 60))
        assertNull(usualViews(posts, "latest"))
    }

    @Test fun average_uses_only_the_ten_most_recent_older_posts() {
        val old = (1..10).map { post("old$it", 1_000_000, 1000L + it) }       // ancient, huge
        val recent = (1..10).map { post("r$it", 100, 10L + it) }              // the ten that count
        val avg = usualViews(listOf(post("latest", 1, 1)) + old + recent, "latest")
        assertEquals(100L, avg)
    }

    @Test fun average_skips_posts_with_unknown_views() {
        val posts = listOf(post("latest", 0, 1), post("a", 100, 5), post("b", -1, 6), post("c", 300, 7), post("d", 200, 8))
        assertEquals(200L, usualViews(posts, "latest"))
    }

    // ---- vsAverageLabel ----

    @Test fun usual_is_not_dragged_up_by_one_viral_post() {
        // Four ordinary posts and one 70K hit: a mean would say ~14K, but a normal post gets ~100.
        val posts = listOf(
            post("latest", 50, 1), post("a", 100, 30), post("b", 120, 40), post("c", 90, 50),
            post("d", 110, 60), post("viral", 70_000, 70),
        )
        assertEquals(110L, usualViews(posts, "latest"))
    }

    @Test fun usual_of_an_even_number_of_posts_is_the_middle_pair() {
        val posts = listOf(post("latest", 1, 1), post("a", 100, 30), post("b", 200, 40), post("c", 300, 50), post("d", 400, 60))
        assertEquals(250L, usualViews(posts, "latest"))
    }

    @Test fun vs_average_formats_the_ratio() {
        assertEquals("2.1× usual", vsAverageLabel(2_100, 1_000, 50 * hour))
        assertEquals("2× usual", vsAverageLabel(2_000, 1_000, 50 * hour))
        assertEquals("12× usual", vsAverageLabel(12_000, 1_000, 50 * hour))
        assertEquals("0.4× usual", vsAverageLabel(400, 1_000, 50 * hour))
    }

    @Test fun vs_average_flags_a_young_post_as_early() {
        assertEquals("0.4× usual · early", vsAverageLabel(400, 1_000, 2 * hour))
        assertEquals("0.4× usual", vsAverageLabel(400, 1_000, 10 * hour))
    }

    @Test fun vs_average_early_note_can_be_dropped_for_the_widget() {
        assertEquals("0.4× usual", vsAverageLabel(400, 1_000, 2 * hour, showEarly = false))
    }

    @Test fun insight_line_follows_the_switches_and_compact_mode() {
        val posts = listOf(post("l", 400, 2), post("a", 1_000, 30), post("b", 1_000, 40), post("c", 1_000, 50))
        val ageNow = now
        assertEquals("0.4× usual · early · +90/h", insightLine(400, posts, "l", now - 2 * hour, 90, true, true, now = ageNow).replace(' ', ' '))
        assertEquals("0.4× usual · +90/h", insightLine(400, posts, "l", now - 2 * hour, 90, true, true, compact = true, now = ageNow).replace(' ', ' '))
        assertEquals("+90/h", insightLine(400, posts, "l", now - 2 * hour, 90, false, true, now = ageNow).replace(' ', ' '))
        assertEquals("0.4× usual · early", insightLine(400, posts, "l", now - 2 * hour, 90, true, false, now = ageNow).replace(' ', ' '))
        assertEquals("", insightLine(400, posts, "l", now - 2 * hour, 90, false, false, now = ageNow).replace(' ', ' '))
    }

    @Test fun vs_average_says_so_plainly_when_there_are_no_views_yet() {
        assertEquals("No views yet", vsAverageLabel(0, 1_000, 10 * 60_000))
        assertEquals("No views yet", vsAverageLabel(0, 1_000, 50 * hour))
    }

    @Test fun vs_average_does_not_print_a_zero_ratio() {
        assertEquals("<0.1× usual", vsAverageLabel(40, 1_000, 50 * hour))
        assertEquals("<0.1× usual · early", vsAverageLabel(40, 1_000, 2 * hour))
        assertEquals("0.1× usual", vsAverageLabel(100, 1_000, 50 * hour))
    }

    @Test fun insight_parts_are_one_badge_each_for_the_cards() {
        val posts = listOf(post("l", 400, 2), post("a", 1_000, 30), post("b", 1_000, 40), post("c", 1_000, 50))
        assertEquals(
            listOf("0.4× usual · early", "+1,244/h"),
            insightParts(400, posts, "l", now - 2 * hour, 1_244, true, true, now = now),
        )
        assertEquals(listOf("+1,244/h"), insightParts(400, posts, "l", now - 2 * hour, 1_244, false, true, now = now))
        assertEquals(emptyList<String>(), insightParts(400, posts, "l", now - 2 * hour, 1_244, false, false, now = now))
    }

    @Test fun vs_average_is_null_without_something_to_compare() {
        assertNull(vsAverageLabel(400, null, hour))
        assertNull(vsAverageLabel(400, 0, hour))
        assertNull(vsAverageLabel(-1, 1_000, hour))
    }

    @Test fun above_average_flag() {
        assertEquals(true, isAboveAverage(1_000, 1_000))
        assertEquals(false, isAboveAverage(999, 1_000))
        assertNull(isAboveAverage(5, null))
    }

    // ---- velocityStep ----

    private val t0 = now - 3 * hour

    @Test fun speed_first_reading_has_no_rate_yet() {
        val step = velocityStep(null, -1, "v", 100, now)
        assertEquals(-1L, step.rate)
        assertEquals(Sample("v", now, 100), step.sample)
    }

    @Test fun speed_keeps_the_last_rate_when_readings_are_too_close() {
        val prev = Sample("v", now - 10 * 60_000, 100)
        val step = velocityStep(prev, 77, "v", 500, now)
        assertEquals(77L, step.rate)
        assertEquals(prev, step.sample)
    }

    @Test fun speed_is_views_gained_per_hour() {
        val prev = Sample("v", now - hour, 100)
        val step = velocityStep(prev, -1, "v", 220, now)
        assertEquals(120L, step.rate)
        assertEquals(prev, step.sample) // not past 90 minutes, so the baseline stays
    }

    @Test fun speed_moves_the_baseline_after_ninety_minutes() {
        val prev = Sample("v", now - 2 * hour, 100)
        val step = velocityStep(prev, -1, "v", 340, now)
        assertEquals(120L, step.rate)
        assertEquals(Sample("v", now, 340), step.sample)
    }

    @Test fun speed_restarts_for_a_different_video() {
        val step = velocityStep(Sample("old", t0, 5_000), 400, "new", 10, now)
        assertEquals(-1L, step.rate)
        assertEquals("new", step.sample?.id)
    }

    @Test fun speed_never_goes_negative() {
        val step = velocityStep(Sample("v", now - hour, 500), -1, "v", 400, now)
        assertEquals(0L, step.rate)
    }

    @Test fun speed_ignores_an_unknown_reading() {
        assertEquals(VelocityStep(-1, null), velocityStep(Sample("v", t0, 1), 5, "v", -1, now))
        assertEquals(VelocityStep(-1, null), velocityStep(Sample("v", t0, 1), 5, "", 10, now))
    }

    @Test fun rate_label() {
        assertEquals("+120/h", rateLabel(120))
        assertEquals("+1,500/h", rateLabel(1_500))
        assertEquals("", rateLabel(0))
        assertEquals("", rateLabel(-1))
    }

    // ---- engagement ----

    @Test fun engagement_percentage() {
        assertEquals("3.5%", engagementLabel(35, 1_000))
        assertEquals("20%", engagementLabel(2_000, 10_000))
        assertEquals("0.0%", engagementLabel(0, 1_000))
    }

    @Test fun engagement_unknown_cases() {
        assertNull(engagementLabel(10, 0))
        assertNull(engagementLabel(-1, 100))
    }

    // ---- best post, frequency ----

    @Test fun best_post_needs_two_posts_and_picks_most_views() {
        assertNull(bestPost(listOf(post("a", 5, 1))))
        assertEquals("b", bestPost(listOf(post("a", 5, 1), post("b", 50, 9), post("c", 20, 3)))?.id)
    }

    @Test fun posts_in_last_days() {
        val posts = listOf(post("a", 1, 2), post("b", 1, 24 * 6), post("c", 1, 24 * 8), post("d", 1, 24 * 30))
        assertEquals(2, postsInLastDays(posts, 7, now))
        assertEquals(1, postsInLastDays(posts, 1, now))
    }

    // ---- best posting window ----

    /** A post [daysAgo] days back, at [hourOfDay] o'clock UTC. */
    private fun at(id: String, views: Long, daysAgo: Int, hourOfDay: Int): Post {
        val startOfToday = now - now % day
        return Post(id, id, views, startOfToday - daysAgo * day + hourOfDay * hour)
    }

    @Test fun best_window_is_the_slot_with_the_highest_average() {
        val posts = listOf(
            at("e1", 1_000, 2, 18), at("e2", 900, 3, 19), at("e3", 1_100, 4, 20),
            at("m1", 100, 5, 8), at("m2", 200, 6, 9), at("m3", 150, 7, 10),
        )
        assertEquals("6 PM–9 PM", bestPostingWindow(posts, now, utc))
    }

    @Test fun best_window_needs_six_posts() {
        val posts = listOf(at("a", 1, 2, 18), at("b", 1, 3, 19), at("c", 1, 4, 20), at("d", 1, 5, 8), at("e", 1, 6, 9))
        assertNull(bestPostingWindow(posts, now, utc))
    }

    @Test fun best_window_ignores_a_single_lucky_post() {
        // Six posts in six different slots: no slot has two, so nothing is claimed.
        val posts = (0 until 6).map { at("p$it", 100L * (it + 1), it + 2, it * 3) }
        assertNull(bestPostingWindow(posts, now, utc))
    }

    @Test fun best_window_ignores_posts_under_a_day_old() {
        val posts = listOf(
            at("n1", 1, 0, 18), at("n2", 1, 0, 19),
            at("a", 100, 2, 6), at("b", 100, 3, 7), at("c", 100, 4, 8),
            at("d", 100, 5, 6), at("e", 100, 6, 7), at("f", 100, 7, 8),
        )
        // The two brand-new posts would otherwise add an evening slot; only the morning counts.
        assertEquals("6 AM–9 AM", bestPostingWindow(posts, now, utc))
    }

    @Test fun hour_labels() {
        assertEquals("12 AM", hourLabel(0))
        assertEquals("9 AM", hourLabel(9))
        assertEquals("12 PM", hourLabel(12))
        assertEquals("9 PM", hourLabel(21))
    }

    // ---- connection problems ----

    @Test fun stale_waits_then_fires_once() {
        var s = staleStep(hasError = true, since = 0, notified = false, now = now)
        assertFalse(s.fire)
        assertEquals(now, s.since)
        s = staleStep(true, s.since, s.notified, now + 2 * hour)
        assertFalse(s.fire)
        s = staleStep(true, s.since, s.notified, now + 4 * hour)
        assertTrue(s.fire)
        s = staleStep(true, s.since, s.notified, now + 5 * hour)
        assertFalse(s.fire)
        assertTrue(s.notified)
    }

    @Test fun stale_resets_after_a_clean_refresh() {
        var s = staleStep(true, now, false, now + 4 * hour)
        assertTrue(s.fire)
        s = staleStep(false, s.since, s.notified, now + 5 * hour)
        assertEquals(StaleStep(0, false, false), s)
        // A later failure is a new episode and can fire again.
        s = staleStep(true, s.since, s.notified, now + 6 * hour)
        assertFalse(s.fire)
        s = staleStep(true, s.since, s.notified, now + 10 * hour)
        assertTrue(s.fire)
    }

    // ---- new comments ----

    @Test fun new_comments_counts_only_growth_on_the_same_video() {
        assertEquals(2L, newComments("v", 3, "v", 5))
        assertEquals(0L, newComments("v", 5, "v", 5))
        assertEquals(0L, newComments("v", 5, "v", 4)) // a deleted comment
    }

    @Test fun new_comments_is_quiet_for_first_sighting_or_other_video() {
        assertEquals(0L, newComments(null, null, "v", 5))
        assertEquals(0L, newComments("old", 0, "new", 9))
        assertEquals(0L, newComments("v", 3, "v", -1))
        assertEquals(0L, newComments("v", -1, "v", 4))
    }

    // ---- widget refresh state ----

    @Test fun refreshing_is_shown_only_for_a_recent_refresh() {
        assertTrue(isRefreshing(now - 5_000, now))
        assertTrue(isRefreshing(now - 89_000, now))
        assertFalse(isRefreshing(now - 90_000, now))   // too old: assume it was lost
        assertFalse(isRefreshing(0, now))              // none running
        assertFalse(isRefreshing(now + 10_000, now))   // a clock that went backwards must not stick
    }

    // ---- history span ----

    private fun d(day: String, yo: Long, yc: Long, fo: Long, fc: Long) = History.Day(day, yo, yc, fo, fc)

    @Test fun span_uses_the_oldest_day_in_range() {
        val h = listOf(
            d("2026-11-10", 50, 60, 500, 600),   // too old for 7 days
            d("2026-11-18", 100, 110, 1_000, 1_100),
            d("2026-11-24", 120, 125, 1_200, 1_250),
            d("2026-11-25", 126, 130, 1_251, 1_260),
        )
        val s = spanChange(h, "2026-11-25", 130, 1_260, 7)
        assertEquals(Span(30, 260, 7), s)
    }

    @Test fun span_reports_the_days_it_really_covers() {
        val h = listOf(d("2026-11-23", 10, 12, 100, 110), d("2026-11-25", 14, 15, 120, 125))
        assertEquals(Span(5, 25, 2), spanChange(h, "2026-11-25", 15, 125, 7))
    }

    @Test fun span_is_empty_without_an_earlier_day() {
        assertEquals(Span(null, null, 0), spanChange(emptyList(), "2026-11-25", 5, 5, 7))
        assertEquals(Span(null, null, 0), spanChange(listOf(d("2026-11-25", 1, 2, 3, 4)), "2026-11-25", 5, 5, 7))
    }

    @Test fun span_falls_back_to_close_when_open_is_unknown_and_skips_unknown_now() {
        val h = listOf(d("2026-11-24", -1, 100, 500, 510))
        assertEquals(Span(20, null, 1), spanChange(h, "2026-11-25", 120, -1, 7))
    }
}
