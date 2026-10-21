package com.juice.intake

import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.RulesFallback
import java.io.IOException
import java.time.ZonedDateTime

/** Outcome of parsing a note. [retryLater] means the rules fallback answered and Groq should be tried again. */
data class IntakeResult(val item: ParsedItem, val retryLater: Boolean)

class IntakeParser(
    private val groq: GroqClient,
    private val onDeviceOnly: () -> Boolean,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    fun parse(rawText: String): IntakeResult {
        val now = clock()
        if (onDeviceOnly()) return IntakeResult(RulesFallback.classify(rawText, now), retryLater = false)
        return try {
            val json = groq.parse(rawText, now)
            IntakeResult(IntakeValidator.validate(json, rawText, now.toInstant()), retryLater = false)
        } catch (e: IOException) {
            IntakeResult(RulesFallback.classify(rawText, now), retryLater = true)
        }
    }
}
