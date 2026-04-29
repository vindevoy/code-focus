package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.MouseEvent

class ShowLoggingLinesToggleTest {
    @Test
    fun `toggle defaults to on`() {
        val toggle = ShowLoggingLinesToggle()
        assertTrue(toggle.isOn)
        assertEquals("Logging lines are shown. Click to hide.", toggle.toolTipText)
    }

    @Test
    fun `clicking flips the state and tooltip`() {
        val toggle = ShowLoggingLinesToggle()
        click(toggle)
        assertFalse(toggle.isOn)
        assertEquals("Logging lines are hidden. Click to show.", toggle.toolTipText)
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
