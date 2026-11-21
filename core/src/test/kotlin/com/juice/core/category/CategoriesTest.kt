package com.juice.core.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {
    private val main = Category(Category.MAIN_ID, Category.MAIN_NAME)
    private val work = Category("work", "Work")
    private val categories = Categories(listOf(main, work))

    @Test
    fun nextStepsForwardAndStopsAtTheLast() {
        assertEquals(work, categories.next("main"))
        assertNull(categories.next("work"))
    }

    @Test
    fun previousStepsBackAndDoesNotWrapFromMain() {
        assertNull(categories.previous("main"))
        assertEquals(main, categories.previous("work"))
    }

    @Test
    fun isLastAndIndexOfServeTheHeaderDots() {
        assertFalse(categories.isLast("main"))
        assertTrue(categories.isLast("work"))
        assertEquals(1, categories.indexOf("work"))
        assertEquals(-1, categories.indexOf("nope"))
        assertNull(categories.next("nope"))
        assertNull(categories.previous("nope"))
        assertFalse(categories.isLast("nope"))
    }

    @Test
    fun blankNameIsRejectedAndOthersAreTrimmed() {
        assertNull(categoryName("   "))
        assertNull(categoryName(""))
        assertEquals("Groceries", categoryName("  Groceries "))
        assertEquals("Ünï  Cödé 🍋", categoryName("Ünï  Cödé 🍋"))
    }

    private val home = Category("home", "Home")
    private val three = Categories(listOf(main, work, home))
    private fun Categories.ids() = list.map { it.id }

    @Test
    fun moveToIndexOneReorders() {
        assertEquals(listOf("main", "home", "work"), three.move("home", 1).ids())
    }

    @Test
    fun moveClampsBeforeMainToIndexOne() {
        assertEquals(listOf("main", "home", "work"), three.move("home", 0).ids())
        assertEquals(listOf("main", "home", "work"), three.move("home", -5).ids())
    }

    @Test
    fun moveClampsPastTheEndToLast() {
        assertEquals(listOf("main", "home", "work"), three.move("work", 99).ids())
    }

    @Test
    fun mainAndUnknownIdsDoNotMove() {
        assertEquals(three, three.move("main", 2))
        assertEquals(three, three.move("nope", 1))
    }

    @Test
    fun moveEarlierStopsRightAfterMain() {
        assertEquals(three, three.moveEarlier("work"))
        assertEquals(listOf("main", "home", "work"), three.moveEarlier("home").ids())
    }

    @Test
    fun moveLaterStepsAndStopsAtTheEnd() {
        assertEquals(listOf("main", "home", "work"), three.moveLater("work").ids())
        assertEquals(three, three.moveLater("home"))
    }

    @Test
    fun canMoveIsFalseForMainUnknownAndMainOnly() {
        assertFalse(three.canMove("main"))
        assertFalse(three.canMove("nope"))
        assertTrue(three.canMove("work"))
        assertFalse(Categories(listOf(main)).canMove("main"))
        assertFalse(categories.canMove("work"))
    }
}
