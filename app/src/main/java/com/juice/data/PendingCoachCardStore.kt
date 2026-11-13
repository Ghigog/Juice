package com.juice.data

import java.io.File

/** Ids of coach cards whose chat is open, kept in a file so a card is not lost if the process dies before the chat ends. */
class PendingCoachCardStore(private val file: File) {
    fun ids(): Set<String> =
        try {
            if (!file.exists()) {
                emptySet()
            } else {
                val lines = file.readLines()
                val header = lines.firstOrNull()
                when {
                    // No header is the previous release's format: every line is an id.
                    header == null || !header.startsWith(HEADER_PREFIX) -> lines.filter { it.isNotBlank() }.toSet()
                    header == HEADER -> lines.drop(1).filter { it.isNotBlank() }.toSet()
                    else -> emptySet() // a newer version than we know
                }
            }
        } catch (e: java.io.IOException) {
            emptySet()
        }

    private companion object {
        const val HEADER_PREFIX = "#juice-pending-cards v"
        const val HEADER = HEADER_PREFIX + "1"
    }

    fun add(id: String) = write(ids() + id)

    fun remove(id: String) {
        val all = ids()
        if (id in all) write(all - id)
    }

    private fun write(ids: Set<String>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText((listOf(HEADER) + ids).joinToString("\n"))
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }
}
