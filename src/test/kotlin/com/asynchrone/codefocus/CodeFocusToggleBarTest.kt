package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.FlowLayout

class CodeFocusToggleBarTest {
    @Test
    fun `bar contains all toggles, the Re-Apply button, the Format button, and the Check button in reading order`() {
        val bar = CodeFocusToggleBar()
        assertEquals(8, bar.componentCount)
        assertSame(bar.showComments, bar.getComponent(0))
        assertSame(bar.showBlankLines, bar.getComponent(1))
        assertSame(bar.showLineNumbers, bar.getComponent(2))
        assertSame(bar.showLoggingLines, bar.getComponent(3))
        assertSame(bar.showImports, bar.getComponent(4))
        assertSame(bar.reApplyButton, bar.getComponent(5))
        assertSame(bar.formatButton, bar.getComponent(6))
        assertSame(bar.checkButton, bar.getComponent(7))
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
