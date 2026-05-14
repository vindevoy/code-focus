package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.MouseEvent

class ShowBlankLinesToggleTest {
    @Test
    fun `toggle defaults to on so blank lines are visible by default`() {
        val toggle = ShowBlankLinesToggle()
        assertTrue(toggle.isOn)
        assertEquals("Decorative blank lines are shown. Click to hide.", toggle.toolTipText)
    }

    @Test
    fun `clicking the toggle flips the state and tooltip`() {
        val toggle = ShowBlankLinesToggle()
        click(toggle)
        assertFalse(toggle.isOn)
        assertEquals(
            "Decorative blank lines are hidden. PEP 8 separators kept. Click to show.",
            toggle.toolTipText,
        )
        click(toggle)
        assertTrue(toggle.isOn)
    }

    private fun click(component: Component) {
        val event =
            MouseEvent(
                component,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                0,
                0,
                1,
                false,
                MouseEvent.BUTTON1,
            )
        component.mouseListeners.forEach { it.mouseClicked(event) }
    }
}
