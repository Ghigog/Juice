package com.juice.ui.reservoir

import kotlin.test.Test
import kotlin.test.assertTrue

class ReservoirAgslTest {
    @Test
    fun noUniformArrayIsIndexedByAVariable() {
        val arrays = Regex("""uniform\s+\w+\s+(\w+)\s*\[""").findAll(AGSL).map { it.groupValues[1] }.toList()
        val offenders = arrays.flatMap { name ->
            Regex("""\b$name\s*\[([^\]]*)\]""").findAll(AGSL).map { it.groupValues[1].trim() }.filterNot { it.matches(Regex("\\d+")) }.toList()
        }
        assertTrue(offenders.isEmpty(), "Non-literal uniform array indices: $offenders")
    }
}
