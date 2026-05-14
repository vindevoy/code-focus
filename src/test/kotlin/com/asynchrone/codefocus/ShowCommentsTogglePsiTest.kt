package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowCommentsToggle]. Boots an in-process
 * IntelliJ Platform with a real `Editor` + PSI + `FoldingModel`, loads the canonical
 * fixture at `resources/python/test.py`, flips the toggle, and asserts on what's
 * collapsed.
 */
@Suppress("ktlint:standard:function-naming")
class ShowCommentsTogglePsiTest : BasePlatformTestCase() {
    private val fixtureText: String = File("resources/python/test.py").readText()

    fun `test toggle off folds standalone comments and docstrings`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowCommentsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts(myFixture.editor)
        val expectedSnippets =
            listOf(
                "# Top-of-file standalone comment",
                "# Standalone comment between imports",
                "# Followed by a second one to form a 2-line group",
                "# Standalone comment inside a function body",
                "# Two consecutive standalone comments deep inside a function",
                "# Trailing standalone comment at the very bottom of the file",
                """Sample module exercising every comment / blank-line shape""",
                """Fetch a URL and return the decoded JSON payload""",
                """Build a human-readable summary""",
            )
        for (snippet in expectedSnippets) {
            assertTrue(
                "Expected a collapsed fold whose text contains `$snippet`. " +
                    "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
                collapsed.any { it.contains(snippet) },
            )
        }
    }

    fun `test toggle on after off restores all comment regions to expanded`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowCommentsToggle(myFixture.editor)
        toggle.isOn = false
        toggle.isOn = true

        val stillCollapsed = collapsedTexts(myFixture.editor)
        // Anything left collapsed must NOT be a comment or a docstring (could be
        // a code-structure fold the IDE created and we never touched).
        for (text in stillCollapsed) {
            assertFalse(
                "After toggle ON, no comment/docstring fold should remain collapsed. Found: `$text`",
                text.trimStart().startsWith("#") || text.trimStart().startsWith("\"\"\""),
            )
        }
    }

    private fun collapsedTexts(editor: Editor): List<String> =
        editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map { editor.document.getText(TextRange(it.startOffset, it.endOffset)) }
}
