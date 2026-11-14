package com.juice.intake

import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.RulesFallback
import com.juice.core.intake.improvingOn
import java.io.IOException
import java.time.ZonedDateTime
import java.util.logging.Logger

/** Outcome of parsing a note. [retryLater] means the rules fallback answered and Groq should be tried again; false for permanent Groq errors. A malformed 200 envelope (no choices or message.content) is retried. */
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
                // A JSON decoding error's later lines quote the reply ("JSON input: ..."), so keep the first line only.
                log.info("Groq reply rejected (${reason.lineSequence().first()})")
            }
            IntakeResult(parsed.improvingOn(RulesFallback.classify(rawText, now), now, rawText), retryLater = false)
        } catch (e: IOException) {
            IntakeResult(RulesFallback.classify(rawText, now), retryLater = !e.isPermanent())
        }
    }

    /** A bad request, key or model fails the same way every time. A malformed 2xx envelope is retried: it is usually a transient Groq hiccup, and IntakeWorker caps the attempts. */
    private fun IOException.isPermanent(): Boolean = when (this) {
        is GroqHttpException -> status in PERMANENT_STATUSES || code == "model_decommissioned" || code == "model_not_found"
        else -> false
    }

    private companion object {
        val PERMANENT_STATUSES = setOf(400, 401, 403, 404)
    }
}
