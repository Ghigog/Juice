package com.juice.core.intake

/** Applies a late parse to a card: user edits win, and the title is never overwritten. */
fun IntakeCard.mergeParse(parsed: ParsedItem): IntakeCard {
    fun touched(field: IntakeField) = field in editedFields
    val mergedType = if (touched(IntakeField.Type)) type else parsed.type
    return copy(
        type = mergedType,
        completionCondition = if (touched(IntakeField.CompletionCondition)) completionCondition else parsed.completionCondition,
        deadline = if (touched(IntakeField.Deadline)) deadline else parsed.deadline,
        subItems = if (touched(IntakeField.SubItems)) subItems else parsed.subItems,
        // A user-set type that is not Routine drops the parsed cadence.
        cadence = if (touched(IntakeField.Type) && type != ItemType.Routine) cadence else parsed.cadence,
    )
}

/**
 * Keeps the rules' Routine when the model answered Note, Task or Goal for text the rules read as one.
 * Otherwise keeps the rules' type and deadline when the model answered Note or Task with no deadline but the
 * [rules] found one in the same text, so the model never comes out worse than the rules.
 */
fun ParsedItem.improvingOn(rules: ParsedItem): ParsedItem =
    if (rules.type == ItemType.Routine && type in setOf(ItemType.Note, ItemType.Task, ItemType.Goal)) {
        copy(type = ItemType.Routine, deadline = null, cadence = rules.cadence)
    } else if (deadline == null && rules.deadline != null && type in setOf(ItemType.Note, ItemType.Task)) {
        copy(type = rules.type, deadline = rules.deadline)
    } else {
        this
    }
