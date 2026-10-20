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
