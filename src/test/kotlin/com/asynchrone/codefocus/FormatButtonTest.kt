package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JLabel

class FormatButtonTest {
    @Test
    fun `button shows the Format label when ruff is available`() {
        val button = FormatButton(ruffAvailable = { true })
        val label = collectLabels(button).firstOrNull { it.text == "Format" }
        assertNotNull(label, "Button should contain a JLabel reading Format")
    }

    @Test
    fun `button has the explanatory tooltip when ruff is available`() {
        val button = FormatButton(ruffAvailable = { true })
        assertEquals("Run ruff format on this file.", button.toolTipText)
    }

    @Test
    fun `button is visible when ruff is available`() {
        val button = FormatButton(ruffAvailable = { true })
        assertTrue(button.isVisible)
    }

    @Test
    fun `button is invisible when ruff is unavailable`() {
        val button = FormatButton(ruffAvailable = { false })
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
