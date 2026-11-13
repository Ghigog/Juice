package com.juice.ui

import com.juice.ui.overlay.JuiceType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fails the build when a hex colour, font size or corner radius is typed outside the token files. */
class DesignTokenGuardTest {
    @Test
    fun mainSourcesUseOnlyTokens() {
        val root = File("src/main")
        assertTrue(root.isDirectory, "run from the :app module directory")
        val violations = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { check(it.name, it.readText()) }.toList()
        assertTrue(violations.isEmpty(), "Design token violations:\n" + violations.joinToString("\n"))
    }

    @Test
    fun juiceTypeSizesAreOnTheTypeLine() {
        val documented = numbers(docLine("- Type:"))
        val sizes = listOf(
            JuiceType.Spurt, JuiceType.Stat, JuiceType.Title, JuiceType.SheetHeading, JuiceType.Dialogue,
            JuiceType.Name, JuiceType.Chat, JuiceType.Body, JuiceType.Button, JuiceType.Label,
            JuiceType.Caption, JuiceType.Badge,
        ).map { it.value.toInt() }
        assertTrue(documented.containsAll(sizes), "JuiceType sizes $sizes not all on the Type line $documented")
    }

    @Test
    fun juiceShapesRadiiAreOnTheRadiusLine() {
        val documented = numbers(docLine("- Radius:"))
        val src = File("src/main/java/com/juice/ui/overlay/OverlayParts.kt").readText()
        val body = src.substringAfter("object JuiceShapes").substringBefore("\n}\n")
        val radii = Regex("""(\d+(?:\.\d+)?)\.dp""").findAll(body).map { it.groupValues[1].toDouble().toInt() }.toSet()
        assertTrue(radii.isNotEmpty())
        assertTrue(documented.containsAll(radii), "JuiceShapes radii $radii not all on the Radius line $documented")
    }

    @Test
    fun hexColourIsReportedOutsideTokenFiles() {
        val snippet = "val a = 1\nval c = Color(0xFF123456)"
        assertEquals(listOf("Foo.kt:2: hex colour outside a token file"), hexColours("Foo.kt", snippet))
        assertEquals(emptyList(), hexColours("OverlayParts.kt", snippet))
        assertEquals(emptyList(), hexColours("FlavourTheme.kt", snippet))
        assertEquals(emptyList(), hexColours("CharacterFace.kt", snippet))
        assertEquals(emptyList(), hexColours("Foo.kt", "val c = Color.White"))
    }

    @Test
    fun fontSizeLiteralIsReportedOutsideOverlayParts() {
        assertEquals(1, fontSizes("Foo.kt", "Text(x, fontSize = 16.sp)").size)
        assertEquals(1, fontSizes("Foo.kt", "a\nText(x, fontSize = if (b) 20.sp else 15.sp)").filter { it.startsWith("Foo.kt:2:") }.size)
        assertEquals(emptyList(), fontSizes("OverlayParts.kt", "fontSize = 16.sp"))
        assertEquals(emptyList(), fontSizes("Foo.kt", "Text(x, fontSize = JuiceType.Chat)"))
    }

    @Test
    fun cornerRadiusLiteralIsReportedOutsideOverlayParts() {
        assertEquals(listOf("Foo.kt:1: dp corner radius outside JuiceShapes"), radii("Foo.kt", "RoundedCornerShape(17.dp)"))
        assertEquals(1, radii("Foo.kt", "RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)").size)
        assertEquals(emptyList(), radii("OverlayParts.kt", "RoundedCornerShape(17.dp)"))
        assertEquals(emptyList(), radii("Foo.kt", "RoundedCornerShape(50)"))
        assertEquals(emptyList(), radii("Foo.kt", "JuiceShapes.Card"))
    }

    private val hexAllowed = setOf("OverlayParts.kt", "FlavourTheme.kt", "CharacterFace.kt")

    private fun check(name: String, text: String) = hexColours(name, text) + fontSizes(name, text) + radii(name, text)

    private fun hexColours(name: String, text: String): List<String> =
        if (name in hexAllowed) emptyList()
        else lines(text, name, Regex("""Color\(0x"""), "hex colour outside a token file")

    private fun fontSizes(name: String, text: String): List<String> =
        if (name == "OverlayParts.kt") emptyList()
        else lines(text, name, Regex("""fontSize\s*=\s*[^,\n]*\d(?:\.\d+)?\.sp"""), "sp font size outside JuiceType")

    private fun radii(name: String, text: String): List<String> =
        if (name == "OverlayParts.kt") emptyList()
        else lines(text, name, Regex("""RoundedCornerShape\([^)\n]*\d(?:\.\d+)?\.dp"""), "dp corner radius outside JuiceShapes")

    private fun lines(text: String, name: String, rx: Regex, message: String): List<String> =
        text.lines().mapIndexedNotNull { i, l -> if (rx.containsMatchIn(l)) "$name:${i + 1}: $message" else null }

    private fun numbers(line: String): Set<Int> = Regex("""\d+""").findAll(line).map { it.value.toInt() }.toSet()

    private fun docLine(prefix: String): String =
        File("../docs/design-system.md").readLines().first { it.startsWith(prefix) }
}
