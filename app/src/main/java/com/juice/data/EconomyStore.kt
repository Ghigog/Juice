package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import com.juice.core.category.Category
import com.juice.core.economy.CategoryEconomy
import com.juice.core.economy.EconomyState
import org.json.JSONObject

/** Every category's Juice and Thirst Meter as last saved, with when, so drain can be applied on open. */
data class SavedEconomy(val economy: CategoryEconomy, val savedAtMillis: Long)

class EconomyStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("economy", Context.MODE_PRIVATE))

    /** Reads the per-category save, or a save from before categories as Main's; null when nothing was saved. */
    fun load(): SavedEconomy? {
        if (!prefs.contains(SAVED_AT)) return null
        val states = prefs.getString(CATEGORIES, null)?.let(::decode)
            ?: mapOf(Category.MAIN_ID to EconomyState(prefs.getLong(JUICE, 0), prefs.getFloat(METER, 0f).toDouble()))
        return SavedEconomy(CategoryEconomy(states = states), prefs.getLong(SAVED_AT, 0))
    }

    /** Writes the new format and drops the legacy `juice`/`meter` keys. */
    fun save(economy: CategoryEconomy, nowMillis: Long) {
        prefs.edit()
            .remove(JUICE)
            .remove(METER)
            .putString(CATEGORIES, encode(economy.states))
            .putLong(SAVED_AT, nowMillis)
            .apply()
    }

    private fun encode(states: Map<String, EconomyState>): String {
        val json = JSONObject()
        states.forEach { (id, s) -> json.put(id, JSONObject().put("juice", s.juice).put("meter", s.meter)) }
        return json.toString()
    }

    private fun decode(text: String): Map<String, EconomyState> {
        val json = JSONObject(text)
        return json.keys().asSequence().associateWith {
            val s = json.getJSONObject(it)
            EconomyState(s.getLong("juice"), s.getDouble("meter"))
        }
    }

    private companion object {
        const val JUICE = "juice"
        const val METER = "meter"
        const val CATEGORIES = "categories"
        const val SAVED_AT = "savedAt"
    }
}
