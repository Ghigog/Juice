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
}
