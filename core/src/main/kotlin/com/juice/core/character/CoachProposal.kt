package com.juice.core.character

import com.juice.core.intake.IntakeValidator
import com.juice.core.routine.Cadence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One proposed change to the player's regular tasks. */
sealed interface CoachTask {
    data class Add(val title: String, val cadence: Cadence) : CoachTask
    data class Change(val id: String, val title: String, val cadence: Cadence) : CoachTask
    data class Remove(val id: String) : CoachTask
}

sealed interface CoachProposalResult {
    data class Valid(val tasks: List<CoachTask>) : CoachProposalResult
    data class Rejected(val reason: String) : CoachProposalResult
}

object CoachProposal {
    const val MAX_TASKS = 10
    const val MAX_TITLE_LENGTH = 120

    /** All-or-nothing: any bad entry rejects the whole proposal. Change and remove ids must be in [knownIds]. */
    fun validate(json: String, knownIds: Set<String>): CoachProposalResult =
        try {
            CoachProposalResult.Valid(parse(json, knownIds))
        } catch (e: IllegalArgumentException) {
            CoachProposalResult.Rejected(e.message ?: "invalid proposal")
        }

    // Json parse errors (SerializationException) and our own checks all extend IllegalArgumentException.
    private fun parse(json: String, knownIds: Set<String>): List<CoachTask> {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val tasks = obj["tasks"] as? JsonArray ?: throw IllegalArgumentException("missing tasks")
        require(tasks.size <= MAX_TASKS) { "too many tasks" }
        return tasks.map { entry ->
            val task = entry as? JsonObject ?: throw IllegalArgumentException("task is not an object")
            when (string(task, "action")) {
                "add" -> CoachTask.Add(title(task), IntakeValidator.cadence(task))
                "change" -> CoachTask.Change(knownId(task, knownIds), title(task), IntakeValidator.cadence(task))
                "remove" -> CoachTask.Remove(knownId(task, knownIds))
                else -> throw IllegalArgumentException("bad action")
            }
        }
    }

    private fun title(task: JsonObject): String {
        val title = string(task, "title").trim().take(MAX_TITLE_LENGTH)
        require(title.isNotEmpty()) { "empty title" }
        return title
    }

    private fun knownId(task: JsonObject, knownIds: Set<String>): String =
        string(task, "id").also { require(it in knownIds) { "unknown id" } }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("missing $key")
}
