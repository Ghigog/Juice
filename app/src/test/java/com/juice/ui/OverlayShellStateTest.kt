package com.juice.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.core.character.Characters
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.CoachCardKind
import com.juice.data.CREATOR_CARD_ID
import com.juice.data.CreatedCharacterStore
import com.juice.intake.CharacterGenerator
import com.juice.core.character.CoachTask
import java.io.IOException
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.data.PendingCoachCardStore
import com.juice.coach.CoachClient
import com.juice.coach.CoachConversation
import com.juice.coach.ConversationStore
import com.juice.memory.MemoryStore
import com.juice.ui.overlay.CoachChatState
import com.juice.ui.overlay.OverlayShellState
import java.io.File
import com.juice.ui.overlay.Swipe
import com.juice.ui.overlay.swipeOf
import com.juice.core.gesture.GesturePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class OverlayShellStateTest {
    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private val completed = mutableListOf<Item>()
    private var undone = 0
    private val task = Item("t", ItemType.Task, "Task")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            // Run queries where they are called, so no save is still in flight when the database closes.
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private val sunday = LocalDate.of(2026, 10, 18)
    private val routine = Item("r", ItemType.Routine, "Water plants", cadence = Cadence(setOf(DayOfWeek.SUNDAY)), nextDueOn = sunday)

    private fun state(vararg items: Item) = OverlayShellState(
        repository,
        CoroutineScope(Dispatchers.Unconfined),
        items.toList(),
        newId = { "new" },
        onCompleted = { completed += it },
        onCompletionUndone = { undone++ },
        today = sunday,
    )

    @Test
    fun completingSavesTheItemAndReportsItForJuice() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.complete(task)
        assertEquals(listOf(task), completed)
        assertEquals(ItemStatus.Completed, repository.get("t")?.status)
        assertEquals(emptyList(), state.items)
    }

    @Test
    fun undoingACompletionRestoresItemAndReportsIt() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.complete(task)
        state.undoLast()
        assertEquals(1, undone)
        assertEquals(ItemStatus.Active, repository.get("t")?.status)
        assertEquals(listOf(task), state.items)
    }

    @Test
    fun dismissingNeitherEarnsNorRevertsJuice() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.dismiss(task)
        state.undoLast()
        assertEquals(emptyList(), completed)
        assertEquals(0, undone)
        assertEquals(ItemStatus.Active, repository.get("t")?.status)
    }

    @Test
    fun completingARoutineHidesItAndMovesItToNextSunday() = runBlocking {
        repository.save(routine)
        val state = state(routine)
        state.complete(routine)
        assertEquals(listOf(routine), completed)
        assertEquals(emptyList(), state.items)
        val saved = repository.get("r")!!
        assertEquals(ItemStatus.Active, saved.status)
        assertEquals(sunday.plusDays(7), saved.nextDueOn)
    }

    @Test
    fun dismissingARoutineEarnsNothingAndSkipsToNextSunday() = runBlocking {
        repository.save(routine)
        val state = state(routine)
        state.dismiss(routine)
        assertEquals(emptyList(), completed)
        assertEquals(emptyList(), state.items)
        assertEquals(sunday.plusDays(7), repository.get("r")?.nextDueOn)
    }

    @Test
    fun undoingARoutineRestoresItsDueDayAndRevertsJuice() = runBlocking {
        repository.save(routine)
        val state = state(routine)
        state.complete(routine)
        state.undoLast()
        assertEquals(1, undone)
        assertEquals(listOf(routine), state.items)
        assertEquals(sunday, repository.get("r")?.nextDueOn)
    }

    @Test
    fun changingARoutineToATaskEndsTheRecurrence() = runBlocking {
        repository.save(routine)
        val state = state(routine)
        state.setType(routine, ItemType.Task)
        val saved = repository.get("r")!!
        assertEquals(ItemType.Task, saved.type)
        assertNull(saved.cadence)
        assertNull(saved.nextDueOn)
        state.complete(state.items.first())
        assertEquals(ItemStatus.Completed, repository.get("r")?.status)
    }

    @Test
    fun capturedTextShowsAsASavedNote() = runBlocking {
        val state = state()
        state.capture("  buy milk ")
        assertEquals("buy milk", state.items.single().title)
        assertEquals(ItemType.Note, repository.get("new")?.type)
        assertNull(state.undo)
    }

    private val steps = listOf("a", "b").map { Step("s$it", it) }
    private val project = Item("p", ItemType.Project, "Move house", steps = steps)

    @Test
    fun aLandedProjectShowsAsOneCardWithItsSteps() = runBlocking {
        val ids = ArrayDeque(listOf("x", "y", "z"))
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(Item("n", ItemType.Note, "raw")),
            newId = { ids.removeFirst() },
        )
        state.land("n", ParsedItem(ItemType.Project, "Move house", subItems = listOf("1", "2", "3")))
        assertEquals(listOf("n"), state.items.map { it.id })
        assertEquals(listOf("1", "2", "3"), state.items.single().steps.map { it.title })
        assertEquals(3, repository.get("n")?.steps?.size)
    }

    @Test
    fun tickingAStepKeepsTheProjectAndTheLastOneCompletesItOnce() = runBlocking {
        repository.save(project)
        val state = state(project)
        state.completeStep(project, "sa")
        assertEquals(listOf(true, false), state.items.single().steps.map { it.done })
        assertEquals(emptyList(), completed)
        state.completeStep(state.items.single(), "sb")
        assertEquals(listOf(project.id), completed.map { it.id })
        assertEquals(emptyList(), state.items)
        assertEquals(ItemStatus.Completed, repository.get("p")?.status)
        state.undoLast()
        assertEquals(1, undone)
        assertEquals(listOf(false), state.items.single().steps.filter { it.id == "sb" }.map { it.done })
        assertEquals(ItemStatus.Active, repository.get("p")?.status)
    }

    @Test
    fun crossingAStepRemovesItFromTheProject() = runBlocking {
        repository.save(project)
        val state = state(project)
        state.removeStep(project, "sa")
        assertEquals(listOf("b"), state.items.single().steps.map { it.title })
        assertEquals(listOf("b"), repository.get("p")?.steps?.map { it.title })
    }

    @Test
    fun stepsOfListsOnlyOpenStepsAndGatesCompletion() {
        val state = state(project.copy(steps = listOf(Step("a", "a", done = true), Step("b", "b"))))
        assertEquals(listOf("b"), state.stepsOf(state.items.single()).map { it.id })
        assertEquals(false, state.canComplete(state.items.single()))
        assertEquals(true, state.canComplete(project.copy(steps = listOf(Step("a", "a", done = true)))))
    }

    @Test
    fun aTypeThePlayerPickedSurvivesALateParse() = runBlocking {
        val note = Item("n", ItemType.Note, "Call Sam")
        repository.save(note)
        val state = state(note)
        state.setType(note, ItemType.Goal)
        assertEquals(ItemType.Goal, state.items.single().type)
        repository.applyParse("n", ParsedItem(ItemType.Task, "Call Sam"))
        assertEquals(ItemType.Goal, repository.get("n")?.type)
    }

    @Test
    fun anEditedTitleSurvivesALateParseWhichStillFillsTheDeadline() = runBlocking {
        val note = Item("n", ItemType.Note, "cal sam")
        repository.save(note)
        val state = state(note)
        state.setTitle(note, " Call Sam ")
        assertEquals("Call Sam", state.items.single().title)
        val deadline = java.time.Instant.ofEpochSecond(1_800_000_000)
        repository.applyParse("n", ParsedItem(ItemType.Task, "Call Samuel", deadline = deadline))
        val saved = repository.get("n")!!
        assertEquals("Call Sam", saved.title)
        assertEquals(deadline, saved.deadline)
        assertEquals(ItemType.Task, saved.type)
    }

    @Test
    fun commitTitleEditSavesTheTrimmedDraftAndEndsEditing() {
        val note = Item("n", ItemType.Note, "cal sam")
        val state = state(note)
        state.beginTitleEdit(note)
        state.changeTitleEdit(" Call Sam ")
        assertEquals(true, state.commitTitleEdit())
        assertEquals("Call Sam", state.items.single().title)
        assertEquals(null, state.titleEdit)
    }

    @Test
    fun commitTitleEditIgnoresEmptyOrUnchangedDrafts() {
        val note = Item("n", ItemType.Note, "cal sam")
        val state = state(note)
        state.beginTitleEdit(note)
        state.changeTitleEdit("  ")
        assertEquals(true, state.commitTitleEdit())
        assertEquals("cal sam", state.items.single().title)
        state.beginTitleEdit(note)
        assertEquals(true, state.commitTitleEdit())
        assertEquals("cal sam", state.items.single().title)
        assertEquals(null, state.titleEdit)
    }

    @Test
    fun commitTitleEditReturnsFalseWhenNothingIsBeingEdited() {
        assertEquals(false, state(Item("n", ItemType.Note, "cal sam")).commitTitleEdit())
    }

    @Test
    fun settingTheTypeToRoutineGivesADailyCadenceDueToday() {
        val task = Item("t", ItemType.Task, "Stretch")
        val state = state(task)
        state.setType(task, ItemType.Routine)
        val routine = state.items.single()
        assertEquals(ItemType.Routine, routine.type)
        assertEquals(DayOfWeek.values().toSet(), routine.cadence?.days)
        assertEquals(null, routine.cadence?.time)
        assertEquals(sunday, routine.nextDueOn)
    }

    @Test
    fun settingTheTypeToRoutineKeepsTheCadenceTheTitleStates() {
        val goal = Item("g", ItemType.Goal, "every two weeks on Friday, change towels and sheets")
        val state = clockedState(goal)
        state.setType(goal, ItemType.Routine)
        val routine = state.items.single()
        assertEquals(ItemType.Routine, routine.type)
        assertEquals(Cadence(setOf(DayOfWeek.FRIDAY), intervalWeeks = 2), routine.cadence)
        assertEquals(DayOfWeek.FRIDAY, routine.nextDueOn!!.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 30), routine.nextDueOn)
    }

    @Test
    fun settingARoutineToTaskClearsItsCadence() {
        val routine = Item("r", ItemType.Routine, "Stretch", cadence = Cadence(setOf(DayOfWeek.MONDAY)), nextDueOn = LocalDate.of(2026, 10, 28))
        val state = state(routine)
        state.setType(routine, ItemType.Task)
        assertEquals(ItemType.Task, state.items.single().type)
        assertEquals(null, state.items.single().cadence)
        assertEquals(null, state.items.single().nextDueOn)
    }

    @Test
    fun aProjectWithOpenStepsCannotBeTickedDirectly() {
        val state = state(project)
        assertEquals(false, state.complete(project))
        assertEquals(emptyList(), completed)
        assertEquals(1, state.items.size)
    }

    @Test
    fun tickingTheLastStepOfAListCompletesIt() = runBlocking {
        val list = Item("l", ItemType.List, "Chores", steps = listOf(Step("a", "a", done = true), Step("b", "b")))
        repository.save(list)
        val state = state(list)
        state.completeStep(list, "b")
        assertEquals(listOf("l"), completed.map { it.id })
        assertEquals(ItemStatus.Completed, repository.get("l")?.status)
    }

    @Test
    fun pickingListClearsDeadlineAndCadence() = runBlocking {
        repository.save(routine)
        val state = state(routine)
        state.setType(routine, ItemType.List)
        val card = state.items.single()
        assertEquals(ItemType.List, card.type)
        assertNull(card.cadence)
        assertNull(card.nextDueOn)
        assertNull(card.deadline)
    }

    @Test
    fun onlyAListOrProjectWithOpenStepsCannotBeCompletedDirectly() {
        val steps = listOf(Step("a", "a"))
        val state = state()
        assertEquals(false, state.canComplete(Item("l", ItemType.List, "L", steps = steps)))
        assertEquals(true, state.canComplete(Item("l", ItemType.List, "L")))
        assertEquals(true, state.canComplete(Item("p", ItemType.Project, "P")))
    }

    @Test
    fun aCaptureWaitingOnTheCloudShowsAsParsingAndNewestComesFirst() = runBlocking {
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(task),
            newId = { "new" }, onCaptured = { true },
        )
        state.capture("buy milk")
        assertEquals(listOf("new", "t"), state.items.map { it.id })
        assertEquals(setOf("new"), state.parsing)
    }

    private val abc = arrayOf("a", "b", "c").map { Item(it, ItemType.Task, it) }.toTypedArray()

    @Test
    fun droppingAHeldCardAtANewIndexMovesItAndSavesTheOrder() = runBlocking {
        abc.forEach { repository.save(it) }
        val s = state(*abc)
        s.pickUp(abc[0])
        s.moveTarget(2)
        assertEquals(true, s.drop())
        assertEquals(listOf("b", "c", "a"), s.items.map { it.id })
        assertNull(s.held)
        assertEquals(listOf("b", "c", "a"), repository.active().map { it.id })
    }

    @Test
    fun droppingInPlaceLeavesTheOrderAndPutsTheCardDown() {
        val s = state(*abc)
        s.pickUp(abc[1])
        s.moveTarget(1)
        assertEquals(false, s.drop())
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
        assertNull(s.held)
    }

    @Test
    fun cancellingAHoldKeepsTheOrder() {
        val s = state(*abc)
        s.pickUp(abc[0])
        s.moveTarget(2)
        s.cancelHold()
        assertNull(s.held)
        assertEquals(false, s.drop())
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
    }

    private fun coachState(coachDone: MutableList<Pair<String, CoachCardKind>>): Pair<OverlayShellState, CharacterRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("characters-shell-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            repository,
        )
        val state = OverlayShellState(
            repository,
            CoroutineScope(Dispatchers.Unconfined),
            emptyList(),
            onCompleted = { completed += it },
            today = sunday,
            characters = characters,
            onCoachCardCompleted = { id, kind -> coachDone += id to kind },
        )
        return state to characters
    }

    @Test
    fun dismissingTheMeetCardDismissesJimAndNoCoachCardsReturn() = runBlocking {
        val jim = Characters.Jim
        val (state, characters) = coachState(mutableListOf())
        characters.unlockAtLevel(jim, 5)
        val meet = repository.get("meet-jim")!!
        state.observe()
        assertEquals(listOf("meet-jim"), state.items.map { it.id })

        state.dismiss(meet)

        assertEquals(emptyList(), state.items)
        assertNull(repository.get("meet-jim"))
        assertEquals(true, characters.state(jim.id).dismissed)
        characters.unlockAtLevel(jim, 6)
        assertNull(repository.get("meet-jim"))
    }

    @Test
    fun dismissingACoachCheckInCardArchivesItLikeAnyOther() = runBlocking {
        val (state, characters) = coachState(mutableListOf())
        val checkIn = Item("checkin-jim", ItemType.Task, "Check in", characterId = Characters.JIM_ID)
        repository.save(checkIn)
        state.dismiss(checkIn)
        assertEquals(ItemStatus.Archived, repository.get("checkin-jim")?.status)
        assertEquals(false, characters.state(Characters.JIM_ID).dismissed)
    }

    @Test
    fun completingTheMeetCardAwardsJuiceAndFiresTheCoachHook() = runBlocking {
        val coachDone = mutableListOf<Pair<String, CoachCardKind>>()
        val (state, characters) = coachState(coachDone)
        characters.unlockAtLevel(Characters.Jim, 5)
        val meet = repository.get("meet-jim")!!

        state.complete(meet)

        assertEquals(listOf(meet), completed)
        assertEquals(listOf(Characters.JIM_ID to CoachCardKind.Meet), coachDone)
    }

    @Test
    fun cancellingTheCoachChatPutsTheMeetCardBack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("characters-shell-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            repository,
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = OverlayShellState(
            repository, scope, emptyList(), today = sunday, characters = characters,
            coachChatFor = { _, onFailed, onConfirmed ->
                val conversation = CoachConversation(
                    Characters.Jim, CoachClient("key", "http://localhost"),
                    ConversationStore(File.createTempFile("conversations", ".json")), MemoryStore(File.createTempFile("memory", ".json")), { false }, { emptyList() },
                )
                CoachChatState(Characters.Jim, conversation, characters, scope, onFailed = onFailed, onConfirmed = onConfirmed, io = Dispatchers.Unconfined)
            },
        )
        characters.unlockAtLevel(Characters.Jim, 5)
        val meet = repository.get("meet-jim")!!
        state.observe()
        state.complete(meet)
        assertEquals(emptyList(), state.items)

        state.coachChat!!.cancel()

        assertEquals(listOf("meet-jim"), state.items.map { it.id })
        assertNull(state.coachChat!!.notice)
    }

    private fun pendingSetup(): Triple<CharacterRepository, PendingCoachCardStore, File> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("characters-shell-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            repository,
        )
        val file = File.createTempFile("pending", ".txt").also { it.delete() }
        return Triple(characters, PendingCoachCardStore(file), file)
    }

    @Test
    fun aLeftoverMarkerRestoresTheCoachCardOnStart() = runBlocking {
        val (characters, pending, file) = pendingSetup()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // First run: the chat opens and the process dies before it ends.
        val first = OverlayShellState(
            repository, scope, emptyList(), today = sunday, characters = characters,
            pendingCards = pending, coachChatFor = { _, _, _ -> null.also { } },
        )
        characters.unlockAtLevel(Characters.Jim, 5)
        val meet = repository.get("meet-jim")!!
        first.observe()
        pending.add(meet.id)
        repository.save(meet.copy(status = ItemStatus.Completed))

        val second = OverlayShellState(repository, scope, emptyList(), today = sunday, characters = characters, pendingCards = PendingCoachCardStore(file))
        second.observe()
        second.restorePending()

        assertEquals(listOf("meet-jim"), second.items.map { it.id })
        assertEquals(emptySet(), PendingCoachCardStore(file).ids())
    }

    @Test
    fun confirmingTheChatClearsTheMarkerSoTheCardStaysGone() = runBlocking {
        val (characters, pending, file) = pendingSetup()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var confirmed: () -> Unit = {}
        val state = OverlayShellState(
            repository, scope, emptyList(), today = sunday, characters = characters, pendingCards = pending,
            coachChatFor = { _, onFailed, onConfirmed ->
                confirmed = onConfirmed
                val conversation = CoachConversation(
                    Characters.Jim, CoachClient("key", "http://localhost"),
                    ConversationStore(File.createTempFile("conversations", ".json")), MemoryStore(File.createTempFile("memory", ".json")), { false }, { emptyList() },
                )
                CoachChatState(Characters.Jim, conversation, characters, scope, onFailed = onFailed, onConfirmed = onConfirmed, io = Dispatchers.Unconfined)
            },
        )
        characters.unlockAtLevel(Characters.Jim, 5)
        state.observe()
        state.complete(repository.get("meet-jim")!!)
        assertEquals(setOf("meet-jim"), pending.ids())

        confirmed()
        val restarted = OverlayShellState(repository, scope, emptyList(), today = sunday, characters = characters, pendingCards = PendingCoachCardStore(file))
        restarted.observe()
        restarted.restorePending()

        assertEquals(emptyList(), restarted.items.filter { it.id == "meet-jim" })
    }

    private fun creatorState(llm: (String) -> String): Pair<OverlayShellState, CharacterRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("characters-shell-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            repository,
            created = CreatedCharacterStore(context.getSharedPreferences("created-shell-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            generator = CharacterGenerator(llm = llm),
        )
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), emptyList(), today = sunday, characters = characters,
            registry = characters.registry(), io = Dispatchers.Unconfined,
        )
        return state to characters
    }

    @Test
    fun noCreatorCardBelowLevelTenAndOneAtTenThatDoesNotDuplicate() = runBlocking {
        val (state, characters) = creatorState { "{}" }
        characters.createCreatorCardIfDue(9)
        state.observe()
        assertEquals(emptyList(), state.items)

        characters.createCreatorCardIfDue(10)
        characters.createCreatorCardIfDue(11)
        state.observe()

        assertEquals(listOf(CREATOR_CARD_ID), state.items.map { it.id })
    }

    @Test
    fun confirmingAHobbyAddsTheNewCharactersMeetCard() = runBlocking {
        val (state, characters) = creatorState { "{}" }
        characters.createCreatorCardIfDue(10)
        state.observe()

        state.complete(state.items.single())
        assertEquals(emptyList(), state.items)
        state.createCharacter("plumbing")

        val created = characters.registry().all().single { it.hobby == "plumbing" }
        assertEquals(listOf("meet-${created.id}"), state.items.map { it.id })
        assertNull(state.creator)
        // The creator card is used up and does not come back.
        characters.createCreatorCardIfDue(10)
        assertEquals(ItemStatus.Archived, repository.get(CREATOR_CARD_ID)?.status)
    }

    @Test
    fun aFailedGenerationShowsTheErrorAndAddsNoCard() = runBlocking {
        val (state, characters) = creatorState { throw IOException("offline") }
        characters.createCreatorCardIfDue(10)
        state.observe()
        state.complete(state.items.single())

        state.createCharacter("plumbing")

        assertEquals("offline", state.creator?.error)
        assertEquals(emptyList(), state.items)
        assertEquals(emptyList(), characters.registry().all().filter { it.hobby != null })
    }

    @Test
    fun confirmedTasksOfACreatedCharacterAreRoutinesLinkedToIt() = runBlocking {
        val (_, characters) = creatorState { "{}" }
        val created = (characters.createCharacter("plumbing") as com.juice.data.CreateResult.Created).character

        characters.createTasks(created.id, listOf(CoachTask.Add("Check taps", Cadence(DayOfWeek.entries.toSet()))))

        val routine = repository.byCharacter(created.id).single { it.type == ItemType.Routine }
        assertEquals(ItemType.Routine, routine.type)
        assertEquals(created.id, routine.characterId)
    }

    private fun stroke(vararg p: Pair<Double, Double>) = p.map { GesturePoint(it.first, it.second) }

    @Test
    fun aLongFlatStrokeIsASwipeAndADrawnCheckOrCrossIsNot() {
        assertEquals(Swipe.RIGHT, swipeOf(stroke(0.0 to 10.0, 100.0 to 15.0), 50.0))
        assertEquals(Swipe.LEFT, swipeOf(stroke(100.0 to 10.0, 0.0 to 5.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 10.0, 20.0 to 10.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 50.0, 40.0 to 90.0, 100.0 to 0.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 0.0, 100.0 to 100.0), 50.0))
    }

    // Due Tuesday, Thursday and Saturday; today (Sunday) none of them is due.
    private val later = Item(
        "l", ItemType.Routine, "Stretch",
        cadence = Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)), nextDueOn = sunday.plusDays(2),
    )

    private fun routinesState(): OverlayShellState = runBlocking {
        repository.save(routine)
        repository.save(later)
        state(routine).also { it.observe() }.routines.also { it.observe() }
    }

    @Test
    fun theRoutinesListHoldsEveryActiveRoutineWhileTheCarouselKeepsOnlyThoseDue() {
        val main = runBlocking { repository.save(routine); repository.save(later); repository.save(task); state() }
        main.observe()
        main.routines.observe()
        assertEquals(listOf("r", "t"), main.items.map { it.id }.sorted().filter { it != "l" })
        assertEquals(false, main.items.any { it.id == "l" })
        assertEquals(listOf("l", "r"), main.routines.items.map { it.id }.sorted())
    }

    @Test
    fun completingARoutineInTheRoutinesViewEndsItWithoutAdvancing() {
        val routines = routinesState()
        routines.complete(later)
        assertEquals(listOf("r"), routines.items.map { it.id })
        val saved = repository.let { runBlocking { it.get("l")!! } }
        assertEquals(ItemStatus.Completed, saved.status)
        assertEquals(later.nextDueOn, saved.nextDueOn)
    }

    @Test
    fun archivingARoutineInTheRoutinesViewArchivesItAndLeavesTheCarouselAlone() {
        val main = state(routine)
        val routines = routinesState()
        routines.dismiss(routine)
        assertEquals(listOf("l"), routines.items.map { it.id })
        assertEquals(ItemStatus.Archived, runBlocking { repository.get("r")!!.status })
        assertEquals(listOf("r"), main.items.map { it.id })
    }

    @Test
    fun undoingInTheRoutinesViewBringsTheRoutineBackRecurring() {
        val routines = routinesState()
        routines.dismiss(later)
        routines.undoLast()
        assertEquals(listOf("l", "r"), routines.items.map { it.id }.sorted())
        val saved = runBlocking { repository.get("l")!! }
        assertEquals(ItemStatus.Active, saved.status)
        assertEquals(later.cadence, saved.cadence)
        assertEquals(later.nextDueOn, saved.nextDueOn)
    }

    @Test
    fun aTitleEditedInTheRoutinesViewIsSavedAndShownInTheCarousel() {
        val routines = routinesState()
        routines.setTitle(later, "Stretch legs")
        assertEquals("Stretch legs", runBlocking { repository.get("l")!!.title })
        val dueDay = OverlayShellState(repository, CoroutineScope(Dispatchers.Unconfined), today = sunday.plusDays(2))
        dueDay.observe()
        assertEquals(listOf("Stretch legs"), dueDay.items.filter { it.id == "l" }.map { it.title })
    }

    private val noon = java.time.Instant.parse("2026-10-28T12:00:00Z")

    private fun clockedState(vararg items: Item) = OverlayShellState(
        repository, CoroutineScope(Dispatchers.Unconfined), items.toList(), today = LocalDate.of(2026, 10, 28), clock = { noon },
    )

    @Test
    fun retitlingAGoalAsEveryFridayMakesItAFridayRoutineWithNoStaleDeadline() = runBlocking {
        val goal = Item("g", ItemType.Goal, "take out the trash on Tuesday", deadline = noon)
        repository.save(goal)
        val state = clockedState(goal)
        state.setTitle(goal, "take out the trash every Friday")
        val saved = repository.get("g")!!
        assertEquals(ItemType.Routine, saved.type)
        assertEquals(setOf(DayOfWeek.FRIDAY), saved.cadence?.days)
        assertEquals(LocalDate.of(2026, 10, 30), saved.nextDueOn)
        assertNull(saved.deadline)
        assertNull(state.items.single().deadline)
    }

    @Test
    fun retitlingATaskWithTomorrowMakesItAGoalDueTomorrow() = runBlocking {
        val task = Item("t", ItemType.Task, "call the dentist")
        repository.save(task)
        val state = clockedState(task)
        state.setTitle(task, "call the dentist tomorrow")
        val saved = repository.get("t")!!
        assertEquals(ItemType.Goal, saved.type)
        assertEquals(LocalDate.of(2026, 10, 29), saved.deadline!!.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        assertNull(saved.cadence)
    }

    @Test
    fun aLandedGoalNamesTheDayOfARelativeDeadlineUnlessTheTitleWasEdited() = runBlocking {
        val parsed = ParsedItem(ItemType.Goal, "take out the trash next week")
        val fresh = Item("n", ItemType.Note, "take out the trash next week")
        val plain = clockedState(fresh)
        plain.land("n", parsed)
        assertEquals(true, plain.items.single().title.startsWith("take out the trash on "))

        val edited = fresh.copy(editedFields = setOf(com.juice.core.intake.IntakeField.Title))
        val kept = clockedState(edited)
        kept.land("n", parsed)
        assertEquals("take out the trash next week", kept.items.single().title)
    }

    private fun spanOf(title: String, type: ItemType, kind: com.juice.core.intake.ChipKind) =
        com.juice.core.intake.RulesFallback.spansOf(title, type).first { it.kind == kind }

    @Test
    fun pickingADeadlineDayRewritesTheTitleAndMarksDeadline() = runBlocking {
        val goal = Item("g", ItemType.Goal, "take out the trash on Tuesday", deadline = noon)
        repository.save(goal)
        val state = clockedState(goal)
        state.openChipPicker(goal, spanOf(goal.title, ItemType.Goal, com.juice.core.intake.ChipKind.Deadline))
        state.setDeadlineDay(goal, state.chipPicker!!.span, LocalDate.of(2026, 10, 29))
        val saved = repository.get("g")!!
        assertEquals("take out the trash on Thursday", saved.title)
        assertEquals(LocalDate.of(2026, 10, 29), saved.deadline!!.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        assertEquals(setOf(com.juice.core.intake.IntakeField.Deadline), saved.editedFields)
        assertNull(state.chipPicker)
    }

    @Test
    fun pickingAnIntervalThenAWeekdayChangesTheCadence() = runBlocking {
        val item = Item("w", ItemType.Routine, "water plants every Friday", cadence = Cadence(setOf(DayOfWeek.FRIDAY)), nextDueOn = LocalDate.of(2026, 10, 30))
        repository.save(item)
        val state = clockedState(item)
        state.setCadenceInterval(item, spanOf(item.title, ItemType.Routine, com.juice.core.intake.ChipKind.Interval), 2)
        val two = state.items.single()
        state.setCadenceWeekday(two, spanOf(two.title, ItemType.Routine, com.juice.core.intake.ChipKind.Weekday), DayOfWeek.THURSDAY)
        val saved = repository.get("w")!!
        assertEquals("Every 2 weeks on Thu", saved.cadence!!.label())
        assertEquals(setOf(com.juice.core.intake.IntakeField.Cadence), saved.editedFields)
    }

    @Test
    fun aChipSetDeadlineSurvivesALateParse() = runBlocking {
        val goal = Item("g", ItemType.Goal, "take out the trash on Tuesday", deadline = noon)
        repository.save(goal)
        val state = clockedState(goal)
        state.setDeadlineDay(goal, spanOf(goal.title, ItemType.Goal, com.juice.core.intake.ChipKind.Deadline), LocalDate.of(2026, 10, 29))
        val chosen = repository.get("g")!!.deadline
        repository.applyParse("g", ParsedItem(ItemType.Goal, "take out the trash", deadline = java.time.Instant.ofEpochSecond(1_800_000_000)))
        assertEquals(chosen, repository.get("g")!!.deadline)
    }

    @Test
    fun aLandedRoutineNotDueTodayDrainsAndDrainDoneClearsIt() = runBlocking {
        val note = Item("n", ItemType.Note, "water plants every Tuesday")
        repository.save(note)
        val state = OverlayShellState(repository, CoroutineScope(Dispatchers.Unconfined), listOf(note), today = LocalDate.now()).also { it.observe() }
        val notToday = DayOfWeek.values().first { it != LocalDate.now().dayOfWeek }
        state.land("n", ParsedItem(ItemType.Routine, "water plants", cadence = Cadence(setOf(notToday))))
        assertEquals("n", state.draining?.id)
        assertEquals(emptyList(), state.items)
        state.drainDone()
        assertNull(state.draining)
    }

    @Test
    fun aTitleEditedIntoARoutineNotDueTodayDrains() = runBlocking {
        val goal = Item("g", ItemType.Goal, "take out the trash")
        repository.save(goal)
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(goal), today = sunday, clock = { java.time.Instant.parse("2026-10-18T12:00:00Z") },
        ).also { it.observe() }
        state.setTitle(goal, "take out the trash every Friday")
        assertEquals("g", state.draining?.id)
    }

    @Test
    fun aRoutineDueTodayDoesNotDrain() = runBlocking {
        val note = Item("n", ItemType.Note, "water plants every Sunday")
        repository.save(note)
        val state = OverlayShellState(repository, CoroutineScope(Dispatchers.Unconfined), listOf(note), today = LocalDate.now()).also { it.observe() }
        state.land("n", ParsedItem(ItemType.Routine, "water plants", cadence = Cadence(setOf(LocalDate.now().dayOfWeek))))
        assertEquals(listOf("n"), state.items.map { it.id })
        assertNull(state.draining)
    }

    @Test
    fun completingOrDismissingNeverDrains() = runBlocking {
        repository.save(routine)
        repository.save(task)
        val state = state(routine, task).also { it.observe() }
        state.complete(routine)
        state.dismiss(task)
        assertNull(state.draining)
    }

    @Test
    fun theRoutinesListDoesNotDrain() {
        val routines = routinesState()
        routines.routines.complete(later)
        assertNull(routines.routines.draining)
    }
}
