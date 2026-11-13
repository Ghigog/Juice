package com.juice.ui.liquid

import kotlin.test.Test
import kotlin.test.assertTrue

class LiquidAgslTest {
    @Test
    fun noUniformArrayIsIndexedByAVariable() {
        val arrays = Regex("""uniform\s+\w+\s+(\w+)\s*\[""").findAll(LiquidShaders.COMPOSITE).map { it.groupValues[1] }.toList()
        val offenders = arrays.flatMap { name ->
            Regex("""\b$name\s*\[([^\]]*)\]""").findAll(LiquidShaders.COMPOSITE).map { it.groupValues[1].trim() }.filterNot { it.matches(Regex("\\d+")) }.toList()
        }
        assertTrue(offenders.isEmpty(), "Non-literal uniform array indices: $offenders")
    }
}
