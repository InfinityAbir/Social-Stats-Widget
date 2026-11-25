package com.abir.socialwidget

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The posting-reminder controls, shown under the switch when it is on: when you post, which days,
 * and how early to be reminded. A line at the bottom says exactly when the next reminder will come,
 * so it is clear the settings did something.
 */
@Composable
internal fun ReminderSettings(display: Display, onChange: (Display) -> Unit, now: Long = System.currentTimeMillis()) {
    val context = LocalContext.current
    val dark = LocalPalette.current.isDark

    fun pickTime() {
        // The dialog follows the app's theme (the app's base theme is light).
        val dialogTheme = if (dark) R.style.PickerDark else R.style.PickerLight
        TimePickerDialog(
            context,
            dialogTheme,
            { _, h, m -> onChange(display.copy(reminderTimes = addTime(display.reminderTimes, h * 60 + m))) },
            19, 0, false,
        ).show()
    }

    Spacer(Modifier.height(12.dp))
    Text("When do you post?", color = TextLo, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))

    // Existing times (tap one to remove it) and the add button, three to a row like the other chips.
    val items: List<Pair<String, Boolean>> =
        display.reminderTimes.map { "${timeLabel(it)}  ✕" to true } +
            if (display.reminderTimes.size < MAX_POSTING_TIMES) listOf("+ Add time" to false) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.withIndex().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (i, item) ->
                    val isAdd = i >= display.reminderTimes.size
                    Pill(
                        text = item.first,
                        selected = item.second,
                        modifier = Modifier.weight(1f),
                        description = if (isAdd) "Add a posting time" else "Remove ${timeLabel(display.reminderTimes[i])}",
                    ) {
                        if (isAdd) pickTime()
                        else onChange(display.copy(reminderTimes = display.reminderTimes.filterIndexed { idx, _ -> idx != i }))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("On these days", color = TextLo, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAY_LETTERS.forEachIndexed { bit, letter ->
            val on = display.reminderDays and (1 shl bit) != 0
            Pill(
                text = letter,
                selected = on,
                modifier = Modifier.weight(1f),
                description = "${DAY_NAMES[bit]}, ${if (on) "on" else "off"}",
            ) {
                onChange(display.copy(reminderDays = display.reminderDays xor (1 shl bit)))
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Remind me", color = TextLo, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LEAD_OPTIONS.forEach { lead ->
            Pill(
                text = "${leadLabel(lead)} before",
                selected = display.reminderLead == lead,
                modifier = Modifier.weight(1f),
                description = "Remind me ${leadLabel(lead)} before",
            ) { onChange(display.copy(reminderLead = lead)) }
        }
    }

    Spacer(Modifier.height(12.dp))
    val next = nextReminder(now, display.plan)
    Text(
        when {
            display.reminderTimes.isEmpty() -> "Add at least one posting time to get reminders."
            display.reminderDays and ALL_DAYS == 0 -> "Pick at least one day to get reminders."
            next == null -> "No upcoming reminder."
            else -> "Next reminder: ${describeNext(now, next)}, for your ${timeLabel(minutesOfDay(next.slotAt))} slot."
        },
        color = if (next != null) Good else Warn,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Reminders can arrive a few minutes late while your phone is saving battery.",
        color = TextLo,
        fontSize = 11.sp,
    )
}

/** A rounded toggle chip with a 48dp-high touch target. */
@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else Panel1, shape)
            .border(1.dp, if (selected) Accent else Line, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Accent else TextLo,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
