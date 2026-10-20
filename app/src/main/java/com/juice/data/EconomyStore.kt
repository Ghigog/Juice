package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import com.juice.core.economy.EconomyState

/** Juice and Thirst Meter as last saved, with when they were saved so drain can be applied on open. */
data class SavedEconomy(val state: EconomyState, val savedAtMillis: Long)

class EconomyStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("economy", Context.MODE_PRIVATE))

    fun load(): SavedEconomy? {
        if (!prefs.contains(SAVED_AT)) return null
        return SavedEconomy(
            EconomyState(prefs.getLong(JUICE, 0), prefs.getFloat(METER, 0f).toDouble()),
            prefs.getLong(SAVED_AT, 0),
        )
    }

    fun save(state: EconomyState, nowMillis: Long) {
        prefs.edit()
            .putLong(JUICE, state.juice)
            .putFloat(METER, state.meter.toFloat())
            .putLong(SAVED_AT, nowMillis)
            .apply()
    }

    private companion object {
        const val JUICE = "juice"
        const val METER = "meter"
        const val SAVED_AT = "savedAt"
    }
}
