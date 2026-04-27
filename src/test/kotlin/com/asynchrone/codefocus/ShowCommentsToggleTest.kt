package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JLabel

class ShowCommentsToggleTest {
    @Test
    fun `toggle defaults to on so comments are visible by default`() {
        val toggle = ShowCommentsToggle()
        assertTrue(toggle.isOn)
        assertEquals("Comments are shown. Click to hide.", toggle.toolTipText)
    }

    @Test
    fun `setting isOn flips the state and updates the tooltip`() {
        val toggle = ShowCommentsToggle()

        toggle.isOn = false
        assertFalse(toggle.isOn)
        assertEquals("Comments are hidden. Click to show.", toggle.toolTipText)

        toggle.isOn = true
        assertTrue(toggle.isOn)
        assertEquals("Comments are shown. Click to hide.", toggle.toolTipText)
    }

    @Test
    fun `clicking the toggle flips the state`() {
        val toggle = ShowCommentsToggle()
        assertTrue(toggle.isOn)

        click(toggle)
        assertFalse(toggle.isOn)

        click(toggle)
        assertTrue(toggle.isOn)
    }

    @Test
    fun `label reads Show Comments`() {
        val toggle = ShowCommentsToggle()
        val label = toggle.components.filterIsInstance<JLabel>().firstOrNull()
        assertNotNull(label, "Toggle should contain a JLabel for the description")
        assertEquals("Show Comments", label!!.text)
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
