package com.juice.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingCoachCardStoreTest {
    private val file = File.createTempFile("pending", ".txt").also { it.deleteOnExit() }

    @Test
    fun addedIdsLoadFromNewStoreOnSameFile() {
        PendingCoachCardStore(file).apply { add("a"); add("b") }
        assertEquals(setOf("a", "b"), PendingCoachCardStore(file).ids())
    }

    @Test
    fun removedIdIsGone() {
        val store = PendingCoachCardStore(file)
        store.add("a"); store.add("b"); store.remove("a")
        assertEquals(setOf("b"), PendingCoachCardStore(file).ids())
    }

    @Test
    fun missingFileIsEmpty() {
        file.delete()
        assertEquals(emptySet(), PendingCoachCardStore(file).ids())
    }

    @Test
    fun unversionedFileFromPreviousReleaseLoadsSameIds() {
        file.writeText("a\nb")
        assertEquals(setOf("a", "b"), PendingCoachCardStore(file).ids())
    }

    @Test
    fun savedFileStartsWithVersion1Header() {
        PendingCoachCardStore(file).add("a")
        assertEquals("#juice-pending-cards v1", file.readLines().first())
    }

    @Test
    fun futureVersionIsEmptyAndIsNotRewritten() {
        val future = "#juice-pending-cards v99\na"
        file.writeText(future)
        assertEquals(emptySet(), PendingCoachCardStore(file).ids())
        assertEquals(future, file.readText())
    }
}
