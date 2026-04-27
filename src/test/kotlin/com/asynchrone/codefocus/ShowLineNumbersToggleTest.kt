package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.MouseEvent

class ShowLineNumbersToggleTest {
    @Test
    fun `toggle defaults to on so line numbers are visible by default`() {
        val toggle = ShowLineNumbersToggle()
        assertTrue(toggle.isOn)
        assertEquals("Line numbers are shown. Click to hide.", toggle.toolTipText)
    }

    @Test
    fun `clicking the toggle flips the state and tooltip`() {
        val toggle = ShowLineNumbersToggle()
        click(toggle)
        assertFalse(toggle.isOn)
        assertEquals("Line numbers are hidden. Click to show.", toggle.toolTipText)
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
