package com.juice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Package layering rules for `:app`: reads each source file's `import com.juice.` lines and checks them against [rules]. */
class LayeringTest {
    /** A package that must not import any of [forbidden] packages (or their sub-packages). */
    private data class Rule(val pkg: String, val forbidden: List<String>)

    private val rules = listOf(
        Rule("data", listOf("intake", "coach", "memory", "ui", "overlay", "settings", "feedback", "onboarding")),
        Rule("feedback", listOf("ui")),
        Rule("settings", listOf("ui")),
    )

    private val layered = listOf("data", "intake", "coach", "ui", "settings", "feedback")

    /** Package-level edges among [layered], read from import lines; `com.juice.ui.*` counts as ui. */
    private fun edges(): Map<String, Set<String>> = layered.associateWith { pkg ->
        sourceFiles(pkg).flatMap { importsOf(it) }
            .map { it.removePrefix("import com.juice.").substringBefore('.') }
            .filter { it in layered && it != pkg }
            .toSet()
    }

    private fun reaches(edges: Map<String, Set<String>>, from: String, to: String): Boolean {
        val seen = mutableSetOf<String>()
        val todo = ArrayDeque(listOf(from))
        while (todo.isNotEmpty()) {
            val next = todo.removeFirst()
            if (!seen.add(next)) continue
            if (to in edges[next].orEmpty()) return true
            todo.addAll(edges[next].orEmpty())
        }
        return false
    }

    private fun importsOf(file: File): List<String> =
        file.readLines().map { it.trim() }.filter { it.startsWith("import com.juice.") }

    private fun sourceFiles(pkg: String): List<File> =
        listOf("src/main/java", "src/main/kotlin")
            .map { File("$it/com/juice/$pkg") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun `packages do not import the packages their rule forbids`() {
        val violations = rules.flatMap { rule ->
            sourceFiles(rule.pkg).flatMap { file ->
                importsOf(file)
                    .filter { line ->
                        rule.forbidden.any { line.startsWith("import com.juice.$it.") }
                    }
                    .map { "${file.path}: $it (${rule.pkg} must not import ${it.substringAfter("com.juice.").substringBefore('.')})" }
            }
        }
        assertTrue(violations.isEmpty(), "Layering violations:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `coach imports only the Groq transport types from intake`() {
        val allowed = listOf("GroqTransport", "GroqHttpException", "KeyCheck", "GroqEnvelopeException")
            .map { "import com.juice.intake.$it" }
        val violations = sourceFiles("coach").flatMap { file ->
            importsOf(file).filter { it.startsWith("import com.juice.intake.") && it !in allowed }
                .map { "${file.path}: $it" }
        }
        assertTrue(violations.isEmpty(), "coach may only use the Groq transport from intake:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `no two layered packages depend on each other, directly or through a cycle`() {
        val edges = edges()
        val cycles = layered.flatMap { a -> layered.filter { it > a }.map { a to it } }
            .filter { (a, b) -> reaches(edges, a, b) && reaches(edges, b, a) }
            .map { (a, b) -> "$a↔$b cycle" }
        assertTrue(cycles.isEmpty(), "Package cycles:\n" + cycles.joinToString("\n"))
    }

    @Test
    fun `rules find the source files they check`() {
        rules.forEach { assertTrue(sourceFiles(it.pkg).isNotEmpty(), "no sources found for ${it.pkg}") }
    }
}
