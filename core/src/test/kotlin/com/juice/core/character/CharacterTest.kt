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

    @Test fun parsesEachLeadingEmotionTag() {
        assertEquals(ParsedReply(Emotion.HAPPY, "Well howdy!"), CoachReplyText.parse("[happy] Well howdy!"))
        assertEquals(ParsedReply(Emotion.STERN, "Drop and give me ten."), CoachReplyText.parse("[stern] Drop and give me ten."))
        assertEquals(ParsedReply(Emotion.THINKING, "Hmm."), CoachReplyText.parse("[thinking] Hmm."))
        assertEquals(ParsedReply(Emotion.NEUTRAL, "Okay."), CoachReplyText.parse("[neutral] Okay."))
    }

    @Test fun tagIsCaseInsensitive() {
        assertEquals(ParsedReply(Emotion.HAPPY, "Hi"), CoachReplyText.parse("[HAPPY]   Hi"))
    }

    @Test fun missingOrUnknownTagIsNeutral() {
        assertEquals(ParsedReply(Emotion.NEUTRAL, "Plain reply"), CoachReplyText.parse("Plain reply"))
        assertEquals(ParsedReply(Emotion.NEUTRAL, "Huh?"), CoachReplyText.parse("[confused] Huh?"))
    }

    @Test fun tagInTheMiddleIsLeftAlone() {
        assertEquals(ParsedReply(Emotion.NEUTRAL, "Well [happy] howdy"), CoachReplyText.parse("Well [happy] howdy"))
    }

    @Test fun jimPromptNamesJimothyKeepsGuardrailAndContract() {
        val prompt = jim.personaPrompt
        assertTrue("Jimothy" in prompt)
        assertTrue("not a doctor" in prompt)
        listOf("[neutral]", "[happy]", "[stern]", "[thinking]").forEach { assertTrue(it in prompt, it) }
        assertTrue("""{"tasks":[""" in prompt)
        assertTrue("no emotion tag" in prompt)
    }

    @Test fun jimHasSquareEyesAndTriangleMouth() {
        assertEquals(Face(Shape.SQUARE, Shape.TRIANGLE), jim.face)
    }

    @Test fun jimPromptNamesEveryExpressionTag() {
        Expression.entries.forEach { assertTrue("[${it.name.lowercase()}]" in jim.personaPrompt, it.name) }
    }

    @Test fun jimHasOpeningLineAndPitchInRange() {
        assertTrue(jim.openingLine.isNotBlank())
        assertTrue(jim.mutterPitch in 0.5f..2.0f)
    }

    @Test fun allListsJimNutritionistAndSleepScientistInUnlockOrder() {
        assertEquals(listOf(jim, Characters.Nutritionist, Characters.SleepScientist), Characters.all)
        assertEquals(listOf(5, 10, 15), Characters.all.map { it.unlockLevel })
        assertEquals(Characters.all.size, Characters.all.map { it.id }.toSet().size)
        assertEquals(Characters.all.size, Characters.all.map { it.openingLine }.toSet().size)
        assertEquals(Characters.all.size, Characters.all.map { it.mutterPitch }.toSet().size)
        Characters.all.forEach { assertTrue(it.openingLine.isNotBlank(), it.id) }
    }

    @Test fun newCharactersKeepGuardrailTagsAndContractWithOwnTopics() {
        val topics = mapOf(
            Characters.Nutritionist to listOf("shopping", "cooking", "new foods"),
            Characters.SleepScientist to listOf("bedtime", "devices", "winding down"),
        )
        topics.forEach { (character, words) ->
            val prompt = character.personaPrompt
            assertTrue("not a doctor" in prompt, character.id)
            listOf("[neutral]", "[happy]", "[stern]", "[thinking]").forEach { assertTrue(it in prompt, it) }
            assertTrue("""{"tasks":[""" in prompt, character.id)
            assertTrue("no emotion tag" in prompt, character.id)
            words.forEach { assertTrue(it in prompt, it) }
        }
    }

    @Test fun newCharactersBecomeMetPendingAtTheirUnlockLevel() {
        listOf(Characters.Nutritionist, Characters.SleepScientist).forEach {
            assertEquals(Availability.Locked, CharacterTransitions.onLevel(CharacterState(), it, it.unlockLevel - 1).availability)
            assertEquals(Availability.MetPending, CharacterTransitions.onLevel(CharacterState(), it, it.unlockLevel).availability)
        }
    }
}
