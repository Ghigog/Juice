package com.juice

import android.content.Context
import com.juice.coach.CoachClient
import com.juice.coach.CoachConversation
import com.juice.core.character.Character
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.intake.GroqKey
import com.juice.core.progression.Progression
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.ConversationStore
import com.juice.data.CreatedCharacterStore
import com.juice.data.EconomyStore
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.data.MemoryStore
import com.juice.data.PendingCoachCardStore
import com.juice.intake.CharacterGenerator
import com.juice.intake.IntakeDependencies
import com.juice.intake.GroqClient
import com.juice.notify.ReminderScheduler
import com.juice.settings.CharacterSettings
import com.juice.settings.GroqKeyStore
import com.juice.settings.SettingsRepository
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope

/** Builds the app's stores, repositories and Groq clients once, lazily, for the service, activity, boot receiver and worker to share. */
open class AppContainer(
    context: Context,
    /** Builds the Groq client for a key; the only way the app gets one, so a test can supply its own. */
    override val groqFor: (GroqKey) -> GroqClient = { GroqClient(it.value) },
) : IntakeDependencies {
    private val context: Context = context.applicationContext

    override val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val database: JuiceDatabase by lazy { JuiceDatabase.open(context) }
    val characterStore: CharacterStore by lazy { CharacterStore(context) }
    val createdStore: CreatedCharacterStore by lazy { CreatedCharacterStore(context) }
    val memoryStore: MemoryStore by lazy { MemoryStore(File(context.filesDir, "memory.json")) }
    val conversationStore: ConversationStore by lazy { ConversationStore(File(context.filesDir, "conversations.json")) }
    val pendingCards: PendingCoachCardStore by lazy { PendingCoachCardStore(File(context.filesDir, "pending-coach-cards.txt")) }
    val economyStore: EconomyStore by lazy { EconomyStore(context) }

    override val items: ItemRepository by lazy {
        ItemRepository(
            database.itemDao(),
            hiddenCharacterIds = characterStore::disabledIds,
            hiddenChanges = characterStore.changes(),
            reminders = ReminderScheduler(context, Clock.systemDefaultZone()),
        )
    }

    val groqKeyStore: GroqKeyStore by lazy { GroqKeyStore(context) }

    override val key: () -> GroqKey? = { groqKeyStore.load() }

    /** Makes a coach client from the saved Groq key, or null when there is none. */
    val coachClient: () -> CoachClient? = { groqKeyStore.load()?.let { CoachClient(groqFor(it).transport) } }

    val characters: CharacterRepository by lazy {
        CharacterRepository(
            characterStore, items, conversationStore, memoryStore, createdStore,
            CharacterGenerator(
                llm = { hobby -> groqFor(checkNotNull(groqKeyStore.load())).generatePersona(hobby) },
                onDeviceOnly = { settings.current().onDeviceOnly },
                cloudAvailable = { groqKeyStore.load() != null },
            ),
        )
    }

    /** The player's routine items for [characterId], which the coach names in its prompt and proposals. */
    suspend fun coachTasks(characterId: String): List<Item> = items.byCharacter(characterId).filter { it.type == ItemType.Routine }

    fun coachConversationFor(character: Character): CoachConversation = CoachConversation(
        character,
        coachClient(),
        conversationStore,
        memoryStore,
        onDeviceOnly = { settings.current().onDeviceOnly },
        coachTasks = { coachTasks(character.id) },
    )

    fun characterSettings(scope: CoroutineScope): CharacterSettings =
        CharacterSettings(characters, scope, level = { Progression.levelFor(economyStore.load()?.state?.juice ?: 0L) })
}
