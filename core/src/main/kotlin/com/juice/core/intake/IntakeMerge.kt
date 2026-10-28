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
        cadence = if (touched(IntakeField.Cadence) || touched(IntakeField.Type) && type != ItemType.Routine) cadence else parsed.cadence,
    )
}

/**
 * The rules always decide the type (and a Routine's cadence); the model only supplies the title, the steps and the
 * deadline. Only a Project has steps, a Routine and a Note have no deadline, and a Project the model gave no
 * steps keeps the rules' own.
 */
fun ParsedItem.improvingOn(rules: ParsedItem): ParsedItem = copy(
    type = rules.type,
    deadline = if (rules.type in setOf(ItemType.Routine, ItemType.Note)) null else deadline ?: rules.deadline,
    subItems = if (rules.type == ItemType.Project) subItems.ifEmpty { rules.subItems } else emptyList(),
    cadence = rules.cadence,
)
