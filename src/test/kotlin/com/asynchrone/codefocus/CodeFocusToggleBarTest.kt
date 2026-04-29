package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.FlowLayout

class CodeFocusToggleBarTest {
    @Test
    fun `bar contains both toggles in reading order`() {
        val bar = CodeFocusToggleBar()
        assertEquals(2, bar.componentCount)
        assertSame(bar.showComments, bar.getComponent(0))
        assertSame(bar.showBlankLines, bar.getComponent(1))
    }

    @Test
    fun `bar uses a horizontal flow so the toggles share one row`() {
        val bar = CodeFocusToggleBar()
        val layout = bar.layout
        assertNotNull(layout)
        assertTrue(layout is FlowLayout, "Expected a FlowLayout, got ${layout::class.simpleName}")
        assertEquals(FlowLayout.RIGHT, (layout as FlowLayout).alignment)
    }
}
