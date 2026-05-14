package com.asynchrone.codefocus

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowImportsToggle]. Two fixtures:
 *  - `resources/python/test-imports.py` — focused, easy to audit.
 *  - `resources/python/test.py` — real, comprehensive fixture.
 */
@Suppress("ktlint:standard:function-naming")
class ShowImportsTogglePsiTest : BasePlatformTestCase() {
    private val focusedFixture: String = File("resources/python/test-imports.py").readText()
    private val realFixture: String = File("resources/python/test.py").readText()

    private fun collapsedTexts(): List<String> =
        myFixture.editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map { myFixture.editor.document.getText(TextRange(it.startOffset, it.endOffset)) }

    fun `test focused fixture - toggle off folds the top-level import group`() {
        myFixture.configureByText("test-imports.py", focusedFixture)
        val toggle = ShowImportsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()
        assertTrue(
            "Expected the top-level import group (import json/os/sys + from collections / dataclasses / pathlib) to be collapsed. " +
                "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(140) }}",
            collapsed.any { it.contains("import json") && it.contains("from pathlib import Path") },
        )
    }

    fun `test focused fixture - toggle off folds the inline import inside fetch`() {
        myFixture.configureByText("test-imports.py", focusedFixture)
        val toggle = ShowImportsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()
        assertTrue(
            "Expected the inline `from logging import getLogger` to be in a collapsed fold.",
            collapsed.any { it.contains("from logging import getLogger") },
        )
    }

    fun `test focused fixture - toggle on after off restores import folds`() {
        myFixture.configureByText("test-imports.py", focusedFixture)
        val toggle = ShowImportsToggle(myFixture.editor)
        toggle.isOn = false
        toggle.isOn = true

        val collapsedTopLevelImports =
            collapsedTexts().any {
                it.contains("import json") && it.contains("from pathlib import Path")
            }
        assertFalse(
            "After toggle ON, the top-level import group must not remain collapsed.",
            collapsedTopLevelImports,
        )
    }

    fun `test real fixture - toggle off folds top-level group plus inline import`() {
        myFixture.configureByText("test.py", realFixture)
        val toggle = ShowImportsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()
        assertTrue(
            "Expected the top-level import group of test.py to be collapsed. " +
                "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(140) }}",
            collapsed.any { it.contains("import json") && it.contains("import math") },
        )
        assertTrue(
            "Expected the inline `from logging import getLogger` of test.py to be collapsed.",
            collapsed.any { it.contains("from logging import getLogger") },
        )
    }
}
