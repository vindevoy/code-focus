package com.asynchrone.codefocus

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowBlankLinesToggle]. The toggle folds
 * "decorative" blank-line runs and keeps the ones PEP 8 requires (two blank lines
 * around top-level `def` / `class`, two blank lines after the last import).
 */
@Suppress("ktlint:standard:function-naming")
class ShowBlankLinesTogglePsiTest : BasePlatformTestCase() {
    private val fixtureText: String = File("resources/python/test.py").readText()

    /** All collapsed fold-region ranges as `(firstLine0, lastLine0)` 0-based pairs. */
    private fun collapsedLineRanges(): List<Pair<Int, Int>> {
        val document = myFixture.editor.document
        return myFixture.editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map {
                val firstLine = document.getLineNumber(it.startOffset)
                val lastLine = document.getLineNumber((it.endOffset - 1).coerceAtLeast(it.startOffset))
                firstLine to lastLine
            }
    }

    private fun anyFoldCovers(line: Int): Boolean = collapsedLineRanges().any { (s, e) -> line in s..e }

    fun `test toggle off folds decorative blanks between top-level constants`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // The blank line between `DEFAULT_NAME = "code-focus"` (0-based line 23) and
        // the standalone comment that precedes `TIMEOUT_SECONDS` (line 24 is the blank).
        // Decorative — not adjacent to a def/class/import — so it must be folded.
        assertTrue(
            "Expected the decorative blank at 0-based line 24 to be folded. " +
                "Collapsed ranges: ${collapsedLineRanges()}",
            anyFoldCovers(24),
        )
    }

    fun `test toggle off folds decorative blanks inside function bodies`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // Several decorative blanks inside `fetch` — between local statements, around
        // the `try` / `except` block. PEP 8 doesn't protect these.
        val expectedFoldedLines = listOf(47, 51, 57, 61)
        for (line in expectedFoldedLines) {
            assertTrue(
                "Expected an in-function decorative blank at 0-based line $line to be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }

    fun `test toggle off keeps PEP 8 separator before top-level def fetch visible`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // `def fetch(...)` sits at 0-based line 32 in the fixture. Lines 30 and 31
        // are the two PEP 8 blank-line separators in front of it — must stay visible.
        assertFalse(
            "PEP 8 separator at 0-based line 30 must NOT be folded. " +
                "Collapsed ranges: ${collapsedLineRanges()}",
            anyFoldCovers(30),
        )
        assertFalse(
            "PEP 8 separator at 0-based line 31 must NOT be folded. " +
                "Collapsed ranges: ${collapsedLineRanges()}",
            anyFoldCovers(31),
        )
    }

    fun `test toggle off keeps PEP 8 separator after the import block visible`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // The two blank lines after the LAST top-level import (`import math` on
        // 0-based line 18) sit at lines 19 and 20 — they're the PEP 8 separator after
        // the import block and must stay visible.
        for (line in 19..20) {
            assertFalse(
                "PEP 8 separator at 0-based line $line (after the import block) must NOT be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }

    fun `test toggle on after off removes our blank-line folds`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false
        val collapsedAfterOff =
            myFixture.editor.foldingModel.allFoldRegions
                .count { it.isValid && !it.isExpanded }
        assertTrue("Expected at least one collapsed blank-line fold after OFF", collapsedAfterOff > 0)

        toggle.isOn = true
        val collapsedAfterOn =
            myFixture.editor.foldingModel.allFoldRegions
                .filter { it.isValid && !it.isExpanded }
                .map { fold ->
                    myFixture.editor.document.getText(TextRange(fold.startOffset, fold.endOffset))
                }
        // After re-enabling, no purely-blank region should remain collapsed.
        for (text in collapsedAfterOn) {
            assertFalse(
                "After toggle ON, no blank-only region should still be collapsed. Got: `${text.replace("\n", "\\n")}`",
                text.all { it.isWhitespace() },
            )
        }
    }
}
