package com.juice.data

import java.io.File

/** Ids of coach cards whose chat is open, kept in a file so a card is not lost if the process dies before the chat ends. */
class PendingCoachCardStore(private val file: File) {
    fun ids(): Set<String> =
        try {
            if (file.exists()) file.readLines().filter { it.isNotBlank() }.toSet() else emptySet()
        } catch (e: java.io.IOException) {
            emptySet()
        }

    fun add(id: String) = write(ids() + id)

    fun remove(id: String) {
        val all = ids()
        if (id in all) write(all - id)
    }

    private fun write(ids: Set<String>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(ids.joinToString("\n"))
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }
}
