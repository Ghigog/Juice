package com.juice.ui.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.juice.R
import com.juice.core.model.Item
import com.juice.core.notify.plan
import com.juice.core.economy.EconomyConfig
import com.juice.core.economy.ItemType as EconomyItemType
import com.juice.core.intake.ItemType
import com.juice.core.progression.Flavour
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import com.juice.ui.GameSession
import com.juice.ui.Tier
import com.juice.ui.theme.FlavourTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the header and reservoir show. Read from [GameSession.hud] so it follows the session's state. */
data class Hud(
    val juice: Long = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val flavour: Flavour = Flavour.ELECTRIC_CITRUS,
    val fill: Float = 0f,
    val multiplier: Double = 1.0,
    val tier: Tier = Tier.THIRSTY,
) {
    val theme: FlavourTheme get() = FlavourTheme.of(flavour)
    val accent: Color get() = Color(theme.accent)
    val deep: Color get() = Color(theme.deep)

    fun baseXp(type: ItemType): Long = BASE_XP.getValue(type.toEconomy())

    fun worth(type: ItemType): Long = Math.round(baseXp(type) * multiplier)

    val multiplierLabel: String get() = multiplierLabel(multiplier)

    private companion object {
        val BASE_XP = EconomyConfig().baseXp
    }
}

fun GameSession.hud() = Hud(state.juice, level, levelProgress, flavour, fill, multiplier, tier)

fun multiplierLabel(m: Double): String =
    if (m % 1.0 == 0.0) "${m.toInt()}x" else String.format(Locale.US, "%.1fx", m)

fun ItemType.toEconomy() = when (this) {
    ItemType.Note -> EconomyItemType.NOTE
    ItemType.Task -> EconomyItemType.TASK
    ItemType.List -> EconomyItemType.LIST
    ItemType.Goal -> EconomyItemType.GOAL
    ItemType.Project -> EconomyItemType.PROJECT
    ItemType.Routine -> EconomyItemType.ROUTINE
}

enum class LiquidTouchPhase { DOWN, MOVE, UP }

/** A finger on the centre card at [position] (card-local px) of a card [size] px wide and high. */
data class LiquidTouch(val phase: LiquidTouchPhase, val position: Offset, val size: Size)

/** What the overlay asks of its host: feedback, settings changes, closing, and the meter's clock. */
class OverlayActions(
    val feedback: (FeedbackEvent) -> Unit = {},
    val onSettingsChange: ((JuiceSettings) -> JuiceSettings) -> Unit = {},
    val onClose: () -> Unit = {},
    /** The carousel moved by [steps] cards; sloshes the reservoir. */
    val onScroll: (steps: Int) -> Unit = {},
    /** A finger touched the centre card; sloshes the reservoir under it. */
    val onLiquidTouch: (LiquidTouch) -> Unit = {},
    /** Called about once a second while the overlay is open. */
    val onTick: () -> Unit = {},
    /** Items for the Recap: active ones and those completed at or after the given instant. */
    val recapItems: suspend (since: Instant) -> List<Item> = { emptyList() },
)

/** A rising "+250 x2.4" over the card. */
data class FloatingText(val id: Long, val text: String)

/** Transient moments the host raises: the banner and the floating Juice amounts. */
class OverlayEffects {
    var banner by mutableStateOf<Pair<Long, String>?>(null)
        private set
    val floats = mutableStateListOf<FloatingText>()
    private var nextId = 0L

    fun banner(text: String) {
        banner = ++nextId to text
    }

    fun clearBanner(id: Long) {
        if (banner?.first == id) banner = null
    }

    fun float(text: String) {
        floats += FloatingText(++nextId, text)
    }

    fun clearFloat(id: Long) {
        floats.removeAll { it.id == id }
    }
}

object JuiceColors {
    val Ink = Color(0xFF0B1A22)
    val Sheet = Color(0xFF14161F)
    val Toast = Color(0xE614161E)
    val Ok = Color(0xFF4ADE80)
    val No = Color(0xFFF87171)
    val Muted = Color(0xFFCBD5E1)

    fun white(alpha: Float) = Color.White.copy(alpha = alpha)

    fun of(type: ItemType) = when (type) {
        ItemType.Note -> Color(0xFF94A3B8)
        ItemType.Task -> Color(0xFF38BDF8)
        ItemType.List -> Color(0xFF22D3EE)
        ItemType.Goal -> Color(0xFFFBBF24)
        ItemType.Project -> Color(0xFFA78BFA)
        ItemType.Routine -> Color(0xFF34D399)
    }
}

@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    listOf(400, 500, 700).map { w ->
        Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** "Today 18:00", "Tomorrow 09:00" or "Fri 6 Mar 09:00", in the phone's zone. */
fun deadlineLabel(deadline: Instant, now: ZonedDateTime = ZonedDateTime.now()): String {
    val at = deadline.atZone(now.zone ?: ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
    val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (days) {
        0L -> "Today $time"
        1L -> "Tomorrow $time"
        else -> at.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + " $time"
    }
}

const val TAG_REMINDER_LINE = "reminderLine"

/** When [item]'s reminder will fire, as a [deadlineLabel]; null when [plan] schedules none. */
internal fun reminderLabel(item: Item?, clock: Clock): String? {
    val reminder = plan(item ?: return null, clock.instant(), clock.zone) ?: return null
    return deadlineLabel(reminder.at, ZonedDateTime.now(clock))
}
