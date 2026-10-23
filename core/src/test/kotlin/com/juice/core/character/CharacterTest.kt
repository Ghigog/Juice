package com.juice.core.character

import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CharacterTest {
    private val jim = Characters.Jim
    private val now = Instant.parse("2026-02-15T12:00:00Z")
    private val known = setOf("t1", "t2")

    private fun metAt(daysAgo: Long, base: CharacterState = CharacterState()) =
        CharacterTransitions.conversationFinished(base, now.minusSeconds(daysAgo * 86_400))

    @Test fun jimIsLevelFiveWithFortnightlyCheckIns() {
        assertEquals(5, jim.unlockLevel)
        assertEquals(14, jim.checkInIntervalDays)
        assertEquals(jim, Characters.byId("jim"))
    }

    @Test fun levelFiveUnlocksJimWithPendingMeetCard() {
        val locked = CharacterState()
        assertEquals(Availability.Locked, CharacterTransitions.onLevel(locked, jim, 4).availability)
        assertEquals(Availability.MetPending, CharacterTransitions.onLevel(locked, jim, 5).availability)
    }

    @Test fun dismissedOrDisabledJimIsNotUnlockedByLevel() {
        assertEquals(Availability.Locked, CharacterTransitions.onLevel(CharacterTransitions.dismiss(CharacterState()), jim, 9).availability)
        assertEquals(Availability.Locked, CharacterTransitions.onLevel(CharacterTransitions.disable(CharacterState()), jim, 9).availability)
    }

    @Test fun checkInDueAtFourteenDaysNotAtThirteen() {
        assertTrue(CharacterTransitions.checkInDue(metAt(14), jim, now))
        assertFalse(CharacterTransitions.checkInDue(metAt(13), jim, now))
    }

    @Test fun enablingADisabledCharacterKeepsEverythingElse() {
        val disabled = CharacterTransitions.disable(metAt(30))
        assertEquals(metAt(30), CharacterTransitions.enable(disabled))
    }

    @Test fun checkInNeverDueWhenDismissedOrDisabled() {
        assertFalse(CharacterTransitions.checkInDue(CharacterTransitions.dismiss(metAt(30)), jim, now))
        assertFalse(CharacterTransitions.checkInDue(CharacterTransitions.disable(metAt(30)), jim, now))
    }

    @Test fun restoreClearsDismissal() {
        val restored = CharacterTransitions.restore(CharacterTransitions.dismiss(metAt(30)))
        assertFalse(restored.dismissed)
        assertTrue(CharacterTransitions.checkInDue(restored, jim, now))
    }

    @Test fun validProposalProducesTasks() {
        val result = CoachProposal.validate(
            """{"tasks":[{"action":"add","title":" Walk ","cadence":{"days":["mon","fri"],"time":"07:30"}},
                {"action":"change","id":"t1","title":"Run","cadence":{"days":"daily"}},
                {"action":"remove","id":"t2"}]}""",
            known,
        )
        val tasks = assertIs<CoachProposalResult.Valid>(result).tasks
        assertEquals(CoachTask.Add("Walk", Cadence(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), java.time.LocalTime.of(7, 30))), tasks[0])
        assertEquals(CoachTask.Remove("t2"), tasks[2])
    }

    @Test fun rejectsEmptyTitleAndMalformedCadence() {
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[{"action":"add","title":"  ","cadence":{"days":"daily"}}]}""", known))
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[{"action":"add","title":"Walk","cadence":{"days":["xyz"]}}]}""", known))
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[{"action":"add","title":"Walk"}]}""", known))
    }

    @Test fun rejectsUnknownIdsBadActionsAndLongLists() {
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[{"action":"remove","id":"nope"}]}""", known))
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[{"action":"nuke"}]}""", known))
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("not json", known))
        val many = (1..11).joinToString(",") { """{"action":"remove","id":"t1"}""" }
        assertIs<CoachProposalResult.Rejected>(CoachProposal.validate("""{"tasks":[$many]}""", known))
    }
}
