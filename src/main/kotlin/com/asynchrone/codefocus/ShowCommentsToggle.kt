package com.asynchrone.codefocus

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Slide-toggle pill widget for the "Show Comments" switch.
 *
 * Issue #5 covers the visual part only — clicking flips the pill's state and
 * tooltip but does not affect the editor's contents. State is held per
 * instance and is therefore implicitly scoped to the editor that owns it.
 */
class ShowCommentsToggle : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showComments.label"))

    var isOn: Boolean
        get() = pill.isOn
        set(value) {
            pill.isOn = value
            pill.repaint()
            updateTooltip()
        }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(label)
        add(pill)

        val click =
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    isOn = !isOn
                }
            }
        addMouseListener(click)
        pill.addMouseListener(click)
        label.addMouseListener(click)

        updateTooltip()
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showComments.tooltip.on" else "toggle.showComments.tooltip.off"
        toolTipText = CodeFocusBundle.message(key)
        pill.toolTipText = toolTipText
        label.toolTipText = toolTipText
    }

    private class Pill : JComponent() {
        var isOn: Boolean = false

        init {
            val size = Dimension(JBUI.scale(26), JBUI.scale(14))
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = height
                g2.color = if (isOn) ON_COLOR else OFF_COLOR
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)

                val inset = JBUI.scale(2)
                val knob = height - inset * 2
                val knobX = if (isOn) width - knob - inset else inset
                g2.color = Color.WHITE
                g2.fillOval(knobX, inset, knob, knob)
            } finally {
                g2.dispose()
            }
        }

        companion object {
            private val ON_COLOR = Color(0x4FAEEF)
            private val OFF_COLOR = Color(0x9AA0A6)
        }
    }
}
