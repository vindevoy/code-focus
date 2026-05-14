package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JLabel

class CheckButtonTest {
    @Test
    fun `button shows the Fix label when ruff is resolvable`() {
        val button = CheckButton(ruffResolver = { "/fake/ruff" })
        val label = collectLabels(button).firstOrNull { it.text == "Fix" }
        assertNotNull(label, "Button should contain a JLabel reading Fix")
    }

    @Test
    fun `button has the explanatory tooltip when ruff is resolvable`() {
        val button = CheckButton(ruffResolver = { "/fake/ruff" })
        assertEquals(
            "Run ruff check --fix on this file. Remaining issues open in a popup.",
            button.toolTipText,
        )
    }

    @Test
    fun `button is visible when ruff is resolvable`() {
        val button = CheckButton(ruffResolver = { "/fake/ruff" })
        assertTrue(button.isVisible)
    }

    @Test
    fun `button is invisible when ruff cannot be resolved`() {
        val button = CheckButton(ruffResolver = { null })
        assertFalse(button.isVisible)
    }

    private fun collectLabels(c: java.awt.Container): List<JLabel> {
        val out = mutableListOf<JLabel>()
        for (i in 0 until c.componentCount) {
            val child = c.getComponent(i)
            if (child is JLabel) out += child
            if (child is java.awt.Container) out += collectLabels(child)
        }
        return out
    }
}
