package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Pill-shaped "Re-Apply" button installed above each Python editor.
 *
 * Re-runs every Code Focus toggle's hide logic against the editor's current
 * content. Use case: a toggle is OFF (e.g. Show Comments off, comments
 * hidden), the developer types a new comment line — the new comment shows
 * because the existing fold regions don't cover it. Click Re-Apply to fold
 * the newly typed lines too.
 *
 * Visible always; clicking does nothing when every toggle is in its ON
 * (visible) state, since there's nothing to re-hide.
 */
class ReApplyButton(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val label = JLabel(CodeFocusBundle.message("button.reApply.label"))

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = CodeFocusBundle.message("button.reApply.tooltip")
        label.border = JBUI.Borders.empty(2, 8)
        add(BackgroundPill(label))

        val click =
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    editor?.let { ShowCommentsToggle.reApplyTo(it) }
                }
            }
        addMouseListener(click)
        label.addMouseListener(click)
    }

    private class BackgroundPill(
        private val inner: JLabel,
    ) : JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)) {
        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(inner)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = BG_COLOR
                g2.fillRoundRect(0, 0, width - 1, height - 1, height, height)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }

        companion object {
            private val BG_COLOR = Color(0xE5, 0xEB, 0xF1)
        }
    }
}
