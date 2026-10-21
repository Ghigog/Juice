package com.juice.core.intake

/** Applies a late parse to a card: user edits win, and the title is never overwritten. */
fun IntakeCard.mergeParse(parsed: ParsedItem): IntakeCard {
    fun touched(field: IntakeField) = field in editedFields
    return copy(
        type = if (touched(IntakeField.Type)) type else parsed.type,
        completionCondition = if (touched(IntakeField.CompletionCondition)) completionCondition else parsed.completionCondition,
        deadline = if (touched(IntakeField.Deadline)) deadline else parsed.deadline,
        subItems = if (touched(IntakeField.SubItems)) subItems else parsed.subItems,
    )
}

/**
 * Keeps the rules' type and deadline when the model answered Note or Task with no deadline but the
 * [rules] found one in the same text, so the model never comes out worse than the rules.
 */
fun ParsedItem.improvingOn(rules: ParsedItem): ParsedItem =
    if (deadline == null && rules.deadline != null && type in setOf(ItemType.Note, ItemType.Task)) {
        copy(type = rules.type, deadline = rules.deadline)
    } else {
        this
    }
