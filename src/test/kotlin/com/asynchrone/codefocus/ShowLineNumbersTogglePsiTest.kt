package com.asynchrone.codefocus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Platform-level integration test for [ShowLineNumbersToggle]. Doesn't touch
 * folding — flips `editor.settings.isLineNumbersShown` instead. Verified against a
 * real `Editor` so the actual `editor.settings` instance is exercised, not a
 * mock.
 */
@Suppress("ktlint:standard:function-naming")
class ShowLineNumbersTogglePsiTest : BasePlatformTestCase() {
    private val fixtureText: String = File("resources/python/test.py").readText()

    fun `test toggle off hides line numbers in the editor settings`() {
        myFixture.configureByText("test.py", fixtureText)
        myFixture.editor.settings.isLineNumbersShown = true

        val toggle = ShowLineNumbersToggle(myFixture.editor)
        toggle.isOn = false

        assertFalse(
            "Editor.settings.isLineNumbersShown must be false after toggle OFF",
            myFixture.editor.settings.isLineNumbersShown,
        )
    }

    fun `test toggle on after off shows line numbers again`() {
        myFixture.configureByText("test.py", fixtureText)
        myFixture.editor.settings.isLineNumbersShown = true

        val toggle = ShowLineNumbersToggle(myFixture.editor)
        toggle.isOn = false
        toggle.isOn = true

        assertTrue(
            "Editor.settings.isLineNumbersShown must be true after returning toggle to ON",
            myFixture.editor.settings.isLineNumbersShown,
        )
    }
}
