package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.swing.JLabel

class ReApplyButtonTest {
    @Test
    fun `button shows the Re-Apply label`() {
        val button = ReApplyButton()
        val descendants = collectLabels(button)
        val label = descendants.firstOrNull { it.text == "Re-Apply" }
        assertNotNull(label, "Button should contain a JLabel reading Re-Apply")
    }

    @Test
    fun `button has the explanatory tooltip`() {
        val button = ReApplyButton()
        assertEquals(
            "Re-apply the current toggle states to lines added since the last toggle change.",
            button.toolTipText,
        )
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
