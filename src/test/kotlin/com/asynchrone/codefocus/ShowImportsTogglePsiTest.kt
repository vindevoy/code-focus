package com.asynchrone.codefocus

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowImportsToggle]. Verifies the top-level
 * import group and the nested `from logging import getLogger` inside `def fetch`
 * collapse when the pill is OFF.
 */
@Suppress("ktlint:standard:function-naming")
class ShowImportsTogglePsiTest : BasePlatformTestCase() {
    private val fixtureText: String = File("resources/python/test.py").readText()

    fun `test toggle off folds the top-level import group and inline imports`() {
        myFixture.configureByText("test.py", fixtureText)
        val toggle = ShowImportsToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()

        assertTrue(
            "Expected the top-level import group to be inside a collapsed fold. " +
                "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(140) }}",
            collapsed.any { it.contains("import json") && it.contains("from pathlib import Path") },
        )

        assertTrue(
            "Expected the inline `from logging import getLogger` (inside `def fetch`) to be in a collapsed fold.",
            collapsed.any { it.contains("from logging import getLogger") },
        )
    }

    fun `test toggle on after off restores import folds`() {
        myFixture.configureByText("test.py", fixtureText)
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

    private fun collapsedTexts(): List<String> =
        myFixture.editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map { myFixture.editor.document.getText(TextRange(it.startOffset, it.endOffset)) }
}
