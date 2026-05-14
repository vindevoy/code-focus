package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowCommentsToggle]. Two fixtures:
 *  - `resources/python/test-comments.py` — focused, easy to audit.
 *  - `resources/python/test.py` — real, comprehensive fixture.
 */
@Suppress("ktlint:standard:function-naming")
class ShowCommentsTogglePsiTest : BasePlatformTestCase() {
    private val focusedFixture: String = File("resources/python/test-comments.py").readText()
    private val realFixture: String = File("resources/python/test.py").readText()

    private fun collapsedTexts(editor: Editor): List<String> =
        editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map { editor.document.getText(TextRange(it.startOffset, it.endOffset)) }

    fun `test focused fixture - toggle off folds every comment and docstring`() {
        myFixture.configureByText("test-comments.py", focusedFixture)
        val toggle = ShowCommentsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts(myFixture.editor)
        val expectedSnippets =
            listOf(
                "# Top-of-file standalone comment",
                "# Second consecutive standalone comment",
                "# inline comment after an import",
                "# Standalone comment inside a function body",
                "# inline comment on an assignment",
                "# First of three grouped comments inside a function",
                "# Second grouped comment",
                "# Third grouped comment, closes the trio",
                "Module docstring on a single line",
                "Single-line function docstring",
                "Multi-line docstring",
                "Second paragraph after a blank line",
            )
        for (snippet in expectedSnippets) {
            assertTrue(
                "Expected a collapsed fold whose text contains `$snippet`. " +
                    "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
                collapsed.any { it.contains(snippet) },
            )
        }
    }

    fun `test focused fixture - toggle on after off restores all comment regions`() {
        myFixture.configureByText("test-comments.py", focusedFixture)
        val toggle = ShowCommentsToggle(myFixture.editor)
        toggle.isOn = false
        toggle.isOn = true

        val stillCollapsed = collapsedTexts(myFixture.editor)
        for (text in stillCollapsed) {
            assertFalse(
                "After toggle ON, no comment/docstring fold should remain collapsed. Found: `$text`",
                text.trimStart().startsWith("#") || text.trimStart().startsWith("\"\"\""),
            )
        }
    }

    fun `test real fixture - toggle off folds the standard comment shapes`() {
        myFixture.configureByText("test.py", realFixture)
        val toggle = ShowCommentsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts(myFixture.editor)
        val expectedSnippets =
            listOf(
                "# Top-of-file standalone comment",
                "# Two consecutive standalone comments deep inside a function",
                "# Trailing standalone comment at the very bottom of the file",
                "Sample module exercising every comment / blank-line shape",
                "Fetch a URL and return the decoded JSON payload",
            )
        for (snippet in expectedSnippets) {
            assertTrue(
                "Expected a collapsed fold whose text contains `$snippet` (real fixture). " +
                    "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
                collapsed.any { it.contains(snippet) },
            )
        }
    }
}
