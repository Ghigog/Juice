package com.juice.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Today's date by [clock]: emitted at once, and again whenever the system says the date, the time or the time zone changed.
 * Dates that repeat are dropped, so a collector only sees the day actually move.
 */
internal fun todayChanges(context: Context, clock: Clock): Flow<LocalDate> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(LocalDate.now(clock))
        }
    }
    trySend(LocalDate.now(clock))
    context.registerReceiver(
        receiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        },
    )
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()
