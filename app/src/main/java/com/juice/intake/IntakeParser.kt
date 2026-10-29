package com.juice.intake

import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.RulesFallback
import com.juice.core.intake.improvingOn
import java.io.IOException
import java.time.ZonedDateTime
import java.util.logging.Logger

/** Outcome of parsing a note. [retryLater] means the rules fallback answered and Groq should be tried again; false for permanent Groq errors. */
data class IntakeResult(val item: ParsedItem, val retryLater: Boolean)

class IntakeParser(
    private val groq: GroqClient,
    private val onDeviceOnly: () -> Boolean,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val log = Logger.getLogger("IntakeParser")

    fun parse(rawText: String): IntakeResult {
        val now = clock()
        if (onDeviceOnly()) return IntakeResult(RulesFallback.classify(rawText, now), retryLater = false)
        return try {
            val json = groq.parse(rawText, now)
            val parsed = IntakeValidator.validate(json, rawText, now.toInstant()) { reason ->
                log.info("Groq reply rejected ($reason): $json")
            }
            IntakeResult(parsed.improvingOn(RulesFallback.classify(rawText, now)), retryLater = false)
        } catch (e: IOException) {
            IntakeResult(RulesFallback.classify(rawText, now), retryLater = !e.isPermanent())
        }
    }

    /** A bad request, key or model, or a malformed 2xx envelope, fails the same way every time. */
    private fun IOException.isPermanent(): Boolean = when (this) {
        is GroqEnvelopeException -> true
        is GroqHttpException -> status in PERMANENT_STATUSES || code == "model_decommissioned" || code == "model_not_found"
        else -> false
    }

    private companion object {
        val PERMANENT_STATUSES = setOf(400, 401, 403, 404)
    }
}
