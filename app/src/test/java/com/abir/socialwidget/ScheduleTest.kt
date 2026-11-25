package com.abir.socialwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class ScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val minute = 60_000L

    /** Epoch millis for a UTC date and time. 2026-11-25 is a Wednesday. */
    private fun at(day: Int, hour: Int, min: Int = 0, month: Int = 11): Long =
        ZonedDateTime.of(2026, month, day, hour, min, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    private fun plan(vararg times: Int, days: Int = ALL_DAYS, lead: Int = 30) = PostingPlan(times.toList(), days, lead)

    private val seven = 19 * 60 // 7:00 PM
    private val nine = 9 * 60

    // Day bits: Monday = bit 0 ... Sunday = bit 6.
    private val wednesday = 1 shl 2
    private val friday = 1 shl 4

    // ---- nextReminder ----

    @Test fun reminds_before_todays_slot() {
        val r = nextReminder(at(25, 10), plan(seven), utc)!!
        assertEquals(at(25, 18, 30), r.fireAt)
        assertEquals(at(25, 19), r.slotAt)
    }

    @Test fun lead_time_is_respected() {
        assertEquals(at(25, 18, 0), nextReminder(at(25, 10), plan(seven, lead = 60), utc)!!.fireAt)
        assertEquals(at(25, 18, 50), nextReminder(at(25, 10), plan(seven, lead = 10), utc)!!.fireAt)
    }

    @Test fun after_the_reminder_time_it_moves_to_tomorrow() {
        val r = nextReminder(at(25, 18, 45), plan(seven), utc)!!
        assertEquals(at(26, 18, 30), r.fireAt)
        assertEquals(at(26, 19), r.slotAt)
    }

    @Test fun a_reminder_exactly_now_is_not_in_the_future() {
        assertEquals(at(26, 18, 30), nextReminder(at(25, 18, 30), plan(seven), utc)!!.fireAt)
    }

    @Test fun with_several_times_the_soonest_comes_first() {
        val p = plan(nine, seven)
        assertEquals(at(26, 8, 30), nextReminder(at(25, 20), p, utc)!!.fireAt)  // both passed today
        assertEquals(at(25, 8, 30), nextReminder(at(25, 8), p, utc)!!.fireAt)   // morning slot still ahead
        assertEquals(at(25, 18, 30), nextReminder(at(25, 8, 45), p, utc)!!.fireAt) // morning missed, evening next
    }

    @Test fun only_the_chosen_days_count() {
        // Friday only, from Wednesday: two days ahead.
        assertEquals(at(27, 18, 30), nextReminder(at(25, 10), plan(seven, days = friday), utc)!!.fireAt)
        // Wednesday only, but today's reminder has passed: next Wednesday.
        assertEquals(at(2, 18, 30, month = 12), nextReminder(at(25, 19), plan(seven, days = wednesday), utc)!!.fireAt)
    }

    @Test fun a_slot_just_after_midnight_reminds_the_evening_before() {
        val r = nextReminder(at(25, 10), plan(10, lead = 30), utc)!! // 12:10 AM slot
        assertEquals(at(25, 23, 40), r.fireAt)
        assertEquals(at(26, 0, 10), r.slotAt)
    }

    @Test fun nothing_to_remind_about() {
        assertNull(nextReminder(at(25, 10), PostingPlan(emptyList(), ALL_DAYS, 30), utc))
        assertNull(nextReminder(at(25, 10), plan(seven, days = 0), utc))
    }

    @Test fun it_works_across_a_month_end() {
        val r = nextReminder(at(30, 20), plan(seven), utc)!! // Nov 30, after the reminder
        assertEquals(at(1, 18, 30, month = 12), r.fireAt)
    }

    // ---- shouldRemind ----

    @Test fun a_reminder_after_the_posting_time_is_dropped() {
        assertTrue(shouldRemind(at(25, 18, 40), at(25, 19)))
        assertFalse(shouldRemind(at(25, 19), at(25, 19)))
        assertFalse(shouldRemind(at(25, 19, 5), at(25, 19)))
        assertFalse(shouldRemind(at(25, 18), 0))
    }

    // ---- wording ----

    @Test fun time_labels() {
        assertEquals("12:00 AM", timeLabel(0))
        assertEquals("1:05 AM", timeLabel(65))
        assertEquals("12:00 PM", timeLabel(12 * 60))
        assertEquals("7:00 PM", timeLabel(seven))
        assertEquals("11:59 PM", timeLabel(23 * 60 + 59))
    }

    @Test fun lead_labels() {
        assertEquals("10 min", leadLabel(10))
        assertEquals("30 min", leadLabel(30))
        assertEquals("1 hour", leadLabel(60))
        assertEquals("2 hours", leadLabel(120))
    }

    @Test fun the_title_tells_how_long_is_really_left() {
        assertEquals("Posting time in 30 minutes", reminderTitle(30 * minute))
        assertEquals("Posting time in 24 minutes", reminderTitle(24 * minute)) // the alarm came late
        assertEquals("Posting time in 30 minutes", reminderTitle(29 * minute + 30_000)) // rounds up
        assertEquals("Posting time in 1 hour", reminderTitle(60 * minute))
        assertEquals("Posting time in 1 minute", reminderTitle(40_000))
        assertEquals("Posting time in 1 minute", reminderTitle(0))
    }

    @Test fun the_body_names_the_slot() {
        assertEquals("Your 7:00 PM slot is coming up. Time to get your video or reel ready.", reminderBody(seven))
    }

    @Test fun describing_the_next_reminder() {
        val now = at(25, 10)
        assertEquals("Today at 6:30 PM", describeNext(now, Reminder(at(25, 18, 30), at(25, 19)), utc))
        assertEquals("Tomorrow at 6:30 PM", describeNext(now, Reminder(at(26, 18, 30), at(26, 19)), utc))
        assertEquals("Fri at 6:30 PM", describeNext(now, Reminder(at(27, 18, 30), at(27, 19)), utc))
    }

    @Test fun minutes_of_day() {
        assertEquals(seven, minutesOfDay(at(25, 19), utc))
        assertEquals(0, minutesOfDay(at(25, 0), utc))
    }

    // ---- saved list of times ----

    @Test fun times_are_sorted_unique_and_valid() {
        assertEquals(listOf(540, 1140), cleanTimes(listOf(1140, 540, 540, -5, 1440, 2000)))
    }

    @Test fun at_most_six_times() {
        assertEquals(6, cleanTimes((0 until 10).map { it * 60 }).size)
        assertEquals(listOf(0, 60, 120, 180, 240, 300), addTime(listOf(0, 60, 120, 180, 240, 300), 400)) // full: ignored
    }

    @Test fun adding_a_time_keeps_order_and_ignores_duplicates() {
        assertEquals(listOf(540, 1140), addTime(listOf(1140), 540))
        assertEquals(listOf(540, 1140), addTime(listOf(540, 1140), 540))
    }

    @Test fun times_survive_saving_and_loading() {
        assertEquals("540,1140", encodeTimes(listOf(1140, 540)))
        assertEquals(listOf(540, 1140), decodeTimes("540,1140"))
        assertEquals(emptyList<Int>(), decodeTimes(""))
        assertEquals(listOf(540), decodeTimes("540, junk, ,9999"))
    }
}
