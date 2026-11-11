package com.juice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Package layering rules for `:app`: reads each source file's `import com.juice.` lines and checks them against [rules]. */
class LayeringTest {
    /** A package that must not import any of [forbidden] packages (or their sub-packages). */
    private data class Rule(val pkg: String, val forbidden: List<String>)

    private val rules = listOf(
        Rule("data", listOf("intake", "coach", "memory", "ui", "overlay", "settings", "onboarding")),
        Rule("coach", listOf("intake")),
    )

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
    fun `rules find the source files they check`() {
        rules.forEach { assertTrue(sourceFiles(it.pkg).isNotEmpty(), "no sources found for ${it.pkg}") }
    }
}
