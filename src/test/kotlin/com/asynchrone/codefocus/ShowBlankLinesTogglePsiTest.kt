package com.asynchrone.codefocus

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowBlankLinesToggle]. The toggle folds
 * "decorative" blank-line runs and keeps the ones PEP 8 requires (two blank lines
 * around top-level `def` / `class`, two blank lines after the last import).
 *
 * Two fixtures:
 *  - `resources/python/test-blank-lines.py` — focused, easy to audit.
 *  - `resources/python/test.py` — real, comprehensive fixture.
 */
@Suppress("ktlint:standard:function-naming")
class ShowBlankLinesTogglePsiTest : BasePlatformTestCase() {
    private val focusedFixture: String = File("resources/python/test-blank-lines.py").readText()
    private val realFixture: String = File("resources/python/test.py").readText()

    /** Currently collapsed fold-region ranges as `(firstLine0, lastLine0)` pairs. */
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

    fun `test focused fixture - decorative blank between top-level constants is folded`() {
        myFixture.configureByText("test-blank-lines.py", focusedFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // In test-blank-lines.py, the decorative blank between `MAX = 1` and the
        // standalone comment that introduces `DEFAULT` sits at 0-based line 15.
        assertTrue(
            "Expected the decorative blank at 0-based line 15 to be folded. " +
                "Collapsed ranges: ${collapsedLineRanges()}",
            anyFoldCovers(15),
        )
    }

    fun `test focused fixture - in-function decorative blanks are folded`() {
        myFixture.configureByText("test-blank-lines.py", focusedFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // Two in-function blanks inside `fetch`: line 23 (between `a = 1` and the
        // standalone comment) and line 26 (between `b = 2` and `return`).
        for (line in listOf(23, 26)) {
            assertTrue(
                "Expected the in-function decorative blank at 0-based line $line to be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }

    fun `test focused fixture - PEP 8 separator before top-level def fetch is kept`() {
        myFixture.configureByText("test-blank-lines.py", focusedFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // `def fetch(...)` sits at 0-based line 20. Lines 18 and 19 are the two
        // PEP 8 blank-line separators in front of it — must stay visible.
        for (line in 18..19) {
            assertFalse(
                "PEP 8 separator at 0-based line $line must NOT be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }

    fun `test focused fixture - PEP 8 separator after the import block is kept`() {
        myFixture.configureByText("test-blank-lines.py", focusedFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // The two blank lines after the LAST top-level import (`import sys` on line 10)
        // sit at 0-based lines 11 and 12 — protected by the after-imports rule.
        for (line in 11..12) {
            assertFalse(
                "PEP 8 separator at 0-based line $line (after the import block) must NOT be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }

    fun `test focused fixture - toggle on after off removes blank-line folds`() {
        myFixture.configureByText("test-blank-lines.py", focusedFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false
        val collapsedAfterOff =
            myFixture.editor.foldingModel.allFoldRegions
                .count { it.isValid && !it.isExpanded }
        assertTrue("Expected at least one collapsed blank-line fold after OFF", collapsedAfterOff > 0)

        toggle.isOn = true
        val collapsedTexts =
            myFixture.editor.foldingModel.allFoldRegions
                .filter { it.isValid && !it.isExpanded }
                .map { fold ->
                    myFixture.editor.document.getText(TextRange(fold.startOffset, fold.endOffset))
                }
        for (text in collapsedTexts) {
            assertFalse(
                "After toggle ON, no blank-only region should still be collapsed. Got: `${text.replace("\n", "\\n")}`",
                text.all { it.isWhitespace() },
            )
        }
    }

    fun `test real fixture - decorative blanks fold and PEP 8 separators are kept`() {
        myFixture.configureByText("test.py", realFixture)
        val toggle = ShowBlankLinesToggle(myFixture.editor)
        toggle.isOn = false

        // In test.py: line 24 is the decorative blank between top-level constants
        // `DEFAULT_NAME` and the comment block introducing `TIMEOUT_SECONDS`.
        assertTrue(
            "Expected decorative blank at 0-based line 24 in test.py to be folded. " +
                "Collapsed ranges: ${collapsedLineRanges()}",
            anyFoldCovers(24),
        )

        // In test.py: lines 30-31 are the PEP 8 separator before `def fetch` (line 32).
        for (line in 30..31) {
            assertFalse(
                "PEP 8 separator at 0-based line $line in test.py must NOT be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }

        // In test.py: lines 19-20 are the PEP 8 separator after the last import
        // (`import math` on line 18).
        for (line in 19..20) {
            assertFalse(
                "PEP 8 separator at 0-based line $line in test.py must NOT be folded. " +
                    "Collapsed ranges: ${collapsedLineRanges()}",
                anyFoldCovers(line),
            )
        }
    }
}
