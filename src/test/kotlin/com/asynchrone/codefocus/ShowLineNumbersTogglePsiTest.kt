package com.asynchrone.codefocus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration tests for [ShowLineNumbersToggle]. The toggle flips
 * `editor.settings.isLineNumbersShown`; file content doesn't affect behaviour.
 *
 * Two fixtures (kept symmetric with the other Show* PSI tests so all five share
 * one shape):
 *  - `resources/python/test-line-numbers.py` — trivial focused fixture.
 *  - `resources/python/test.py` — real, comprehensive fixture.
 */
@Suppress("ktlint:standard:function-naming")
class ShowLineNumbersTogglePsiTest : BasePlatformTestCase() {
    private val focusedFixture: String = File("resources/python/test-line-numbers.py").readText()
    private val realFixture: String = File("resources/python/test.py").readText()

    fun `test focused fixture - toggle off hides line numbers in editor settings`() {
        myFixture.configureByText("test-line-numbers.py", focusedFixture)
        myFixture.editor.settings.isLineNumbersShown = true

        val toggle = ShowLineNumbersToggle(myFixture.editor)
        toggle.isOn = false

        assertFalse(
            "Editor.settings.isLineNumbersShown must be false after toggle OFF",
            myFixture.editor.settings.isLineNumbersShown,
        )
    }

    fun `test focused fixture - toggle on after off shows line numbers again`() {
        myFixture.configureByText("test-line-numbers.py", focusedFixture)
        myFixture.editor.settings.isLineNumbersShown = true

        val toggle = ShowLineNumbersToggle(myFixture.editor)
        toggle.isOn = false
        toggle.isOn = true

        assertTrue(
            "Editor.settings.isLineNumbersShown must be true after returning toggle to ON",
            myFixture.editor.settings.isLineNumbersShown,
        )
    }

    fun `test real fixture - toggle drives line numbers setting both directions`() {
        myFixture.configureByText("test.py", realFixture)
        myFixture.editor.settings.isLineNumbersShown = true

        val toggle = ShowLineNumbersToggle(myFixture.editor)
        toggle.isOn = false
        assertFalse(
            "test.py: line numbers must hide after toggle OFF",
            myFixture.editor.settings.isLineNumbersShown,
        )

        toggle.isOn = true
        assertTrue(
            "test.py: line numbers must reappear after toggle ON",
            myFixture.editor.settings.isLineNumbersShown,
        )
    }
}
