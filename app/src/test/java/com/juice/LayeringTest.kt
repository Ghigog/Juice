package com.juice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Package layering rules for `:app`: reads each source file's `import com.juice.` lines and checks them against [rules]. */
class LayeringTest {
    /** A package that must not import any of [forbidden] packages (or their sub-packages). */
    private data class Rule(val pkg: String, val forbidden: List<String>)

    private val transportTypes = listOf("GroqTransport", "GroqHttpException", "KeyCheck", "GroqEnvelopeException")
    private val networkTokens = Regex("""HttpURLConnection|java\.net\.URL\b|openConnection""")

    /** The package under `com/juice/` that [path] belongs to, or null. */
    private fun packageOf(path: String): String? =
        path.substringAfter("/com/juice/", "").substringBefore('/').takeIf { it.isNotEmpty() && '.' !in it }

    /** Every layering rule applied to one file; each violation names the path and 1-based line. Pure over (path, text). */
    private fun layeringViolations(path: String, text: String): List<String> {
        val pkg = packageOf(path)
        val inOverlay = "/com/juice/ui/overlay/" in path
        val fileName = path.substringAfterLast('/')
        return text.lines().withIndex().flatMap { (i, raw) ->
            val line = raw.trim()
            val at = "$path:${i + 1}: $line"
            val found = mutableListOf<String>()
            if (line.startsWith("import com.juice.")) {
                val rule = rules.firstOrNull { it.pkg == pkg }
                if (rule != null && rule.forbidden.any { line.startsWith("import com.juice.$it.") }) {
                    found += "$at ($pkg must not import ${line.substringAfter("com.juice.").substringBefore('.')})"
                }
                if (pkg == "coach" && line.startsWith("import com.juice.intake.") &&
                    transportTypes.none { line == "import com.juice.intake.$it" }
                ) found += "$at (coach may only use the Groq transport types from intake)"
                if (inOverlay && !fileName.endsWith("State.kt") && line.startsWith("import com.juice.data.")) {
                    found += "$at (only *State.kt files in ui/overlay may import com.juice.data)"
                }
            }
            if (pkg != "intake" && pkg != "coach" && networkTokens.containsMatchIn(line)) {
                found += "$at (network access belongs under intake/ or coach/)"
            }
            found
        }
    }

    private val rules = listOf(
        Rule("data", listOf("intake", "coach", "memory", "ui", "overlay", "settings", "feedback", "onboarding")),
        Rule("feedback", listOf("ui")),
        Rule("settings", listOf("ui")),
    )

    private val root = "app/src/main/java/com/juice"
    private val dataPath = "$root/data/Thing.kt"
    private val coachPath = "$root/coach/CoachClient.kt"
    private val overlayDir = "$root/ui/overlay"

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

    private fun allSourceFiles(): List<File> =
        listOf("src/main/java", "src/main/kotlin").map { File(it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun sourceFiles(pkg: String): List<File> =
        listOf("src/main/java", "src/main/kotlin")
            .map { File("$it/com/juice/$pkg") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun `real sources satisfy every layering rule`() {
        val violations = allSourceFiles().flatMap { layeringViolations(it.path.replace('\\', '/'), it.readText()) }
        assertTrue(violations.isEmpty(), "Layering violations:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `data importing a feature or ui is a violation`() {
        val v = layeringViolations(dataPath, "package com.juice.data\n\nimport com.juice.ui.overlay.X\n")
        assertEquals(1, v.size)
        assertTrue(v.single().startsWith("$dataPath:3:"), v.single())
        assertTrue(layeringViolations(dataPath, "import com.juice.data.Y\n").isEmpty())
    }

    @Test
    fun `coach importing intake beyond the transport types is a violation`() {
        val bad = layeringViolations(coachPath, "import com.juice.intake.GroqClient\n")
        assertEquals(1, bad.size)
        assertTrue(layeringViolations(coachPath, "import com.juice.intake.GroqTransport\n").isEmpty())
    }

    @Test
    fun `a non-State overlay file importing data is a violation`() {
        val src = "package com.juice.ui.overlay\n\nimport com.juice.data.ChatMessage\n"
        val v = layeringViolations("$overlayDir/CoachChat.kt", src)
        assertEquals(1, v.size)
        assertTrue(v.single().contains(":3:"), v.single())
        assertTrue(layeringViolations("$overlayDir/CoachChatState.kt", src).isEmpty())
    }

    @Test
    fun `network calls outside intake and coach are a violation`() {
        val src = "val c = url.openConnection()\n"
        val v = layeringViolations("$overlayDir/Thing.kt", src)
        assertEquals(1, v.size)
        assertTrue(v.single().startsWith("$overlayDir/Thing.kt:1:"), v.single())
        assertEquals(1, layeringViolations(dataPath, "import java.net.URL\n").size)
        assertEquals(1, layeringViolations(dataPath, "val c: HttpURLConnection\n").size)
        assertTrue(layeringViolations(coachPath, src).isEmpty())
        assertTrue(layeringViolations("$root/intake/GroqTransport.kt", src).isEmpty())
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
