package com.abir.socialwidget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Emits whenever anything in the saved stats or display choices changes.
 *
 * A Glance widget keeps its session alive for a short while after drawing, and a second update during that
 * time re-uses the old composition instead of calling provideGlance again. Reading the numbers inside the
 * composition, keyed on this flow, makes the widget follow the saved numbers every time.
 */
internal fun prefsTicks(context: Context): Flow<Long> = callbackFlow {
    val prefs = Store.sp(context)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(System.nanoTime()) }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    trySend(0L)
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.conflate()

/** Whether each platform has its keys saved. Cheap: it does not decrypt anything. */
data class Configured(val yt: Boolean, val fb: Boolean)

fun Store.loadConfigured(c: Context): Configured {
    val p = sp(c)
    fun hasSecret(name: String) = p.contains("${name}_enc") || !p.getString(name, "").isNullOrBlank()
    return Configured(
        yt = hasSecret("ytKey") && !p.getString("ytChannel", "").isNullOrBlank(),
        fb = hasSecret("fbToken") && !p.getString("fbPageId", "").isNullOrBlank(),
    )
}
