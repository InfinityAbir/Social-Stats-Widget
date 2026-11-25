package com.abir.socialwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Arms one system alarm for the next posting reminder. It does not depend on the 15-minute refresh
 * or on being online. When it goes off the receiver posts the notification and arms the next one, so
 * the chain keeps itself going. It is armed again when the user changes the plan, after a reboot or
 * an app update, and after every refresh as a safety net.
 *
 * The alarm is the battery-friendly kind (no special permission), so on a phone that is saving
 * battery it can arrive a few minutes late; the notification says how long is really left.
 */
object Reminders {
    internal const val ACTION = "com.abir.socialwidget.POSTING_REMINDER"
    internal const val EXTRA_SLOT = "slot_at"
    private const val REQUEST = 4711

    private fun pending(c: Context, slotAt: Long, create: Boolean = true): PendingIntent? {
        val intent = Intent(c, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_SLOT, slotAt)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(c, REQUEST, intent, flags)
    }

    /** Arm (or clear) the alarm to match the saved plan. Safe to call as often as you like. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val display = Store.loadDisplay(app)
        val next = if (display.notifyReminder) nextReminder(now, display.plan) else null
        if (next == null) {
            cancel(app)
            return
        }
        val alarms = app.getSystemService(AlarmManager::class.java)
        val pi = pending(app, next.slotAt) ?: return
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.fireAt, pi) }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val existing = pending(app, 0, create = false) ?: return
        app.getSystemService(AlarmManager::class.java).cancel(existing)
        existing.cancel()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Reminders.ACTION) {
            val slotAt = intent.getLongExtra(Reminders.EXTRA_SLOT, 0)
            // Only if the user still wants reminders; the alarm may be a leftover of an old plan.
            if (Store.loadDisplay(app).notifyReminder) Notifier.postReminder(app, slotAt)
        }
        // The alarm that just fired is spent; this also covers boot and app update.
        Reminders.schedule(app)
    }
}
