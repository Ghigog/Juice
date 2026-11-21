package com.juice.core.category

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryPaletteTest {
    private val p = CategoryPalette.colours

    @Test
    fun paletteHasEightToTenDistinctColours() {
        assertTrue(p.size in 8..10)
        assertEquals(p.size, p.map { it.accent }.toSet().size)
    }

    @Test
    fun firstUnusedSkipsUsedColoursAndWrapsWhenAllAreUsed() {
        assertEquals(p[0], CategoryPalette.firstUnused(emptyList()))
        assertEquals(p[2], CategoryPalette.firstUnused(listOf(p[1].accent, p[0].accent)))
        assertEquals(p[0], CategoryPalette.firstUnused(p.map { it.accent }))
    }

    @Test
    fun unsetColoursResolveInOrderAroundSavedOnes() {
        val cats = Categories(listOf(
            Category("main", "Main"), Category("a", "A"), Category("b", "B", p[1].accent), Category("c", "C"),
        ))
        assertNull(cats.colourOf("main"))
        assertEquals(p[0], cats.colourOf("a"))
        assertEquals(p[1], cats.colourOf("b"))
        assertEquals(p[2], cats.colourOf("c"))
    }
}
