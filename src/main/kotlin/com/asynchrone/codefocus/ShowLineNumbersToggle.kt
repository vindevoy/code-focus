package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
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
 * Slide-toggle pill for the "Show Line Numbers" switch.
 *
 * Flips `editor.settings.isLineNumbersShown` for the editor that hosts
 * this component. State is persisted on the editor's user data so a
 * re-created notification component picks up the previous choice.
 */
class ShowLineNumbersToggle(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showLineNumbers.label"))

    var isOn: Boolean
        get() = pill.isOn
        set(value) {
            if (pill.isOn == value) return
            pill.isOn = value
            pill.repaint()
            updateTooltip()
            saveState(value)
            applyToEditor()
        }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 2)
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

        val initial = loadState() ?: true
        pill.isOn = initial
        updateTooltip()
        applyToEditor()
    }

    private fun loadState(): Boolean? {
        val ed = editor ?: return null
        ed.getUserData(STATE_KEY)?.let { return it }
        val project = ed.project ?: return null
        val url = CodeFocusToggleState.fileUrl(ed) ?: return null
        return CodeFocusToggleState.getInstance(project).getShowLineNumbers(url)
    }

    private fun saveState(value: Boolean) {
        val ed = editor ?: return
        ed.putUserData(STATE_KEY, value)
        val project = ed.project ?: return
        val url = CodeFocusToggleState.fileUrl(ed) ?: return
        CodeFocusToggleState.getInstance(project).setShowLineNumbers(url, value)
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showLineNumbers.tooltip.on" else "toggle.showLineNumbers.tooltip.off"
        toolTipText = CodeFocusBundle.message(key)
        pill.toolTipText = toolTipText
        label.toolTipText = toolTipText
    }

    fun reApply() = applyToEditor()

    private fun applyToEditor() {
        val ed = editor ?: return
        ed.settings.isLineNumbersShown = pill.isOn
        if (ed is EditorEx) ed.gutterComponentEx.revalidateMarkup()
        ed.component.revalidate()
        ed.component.repaint()
    }

    private class Pill : JComponent() {
        var isOn: Boolean = true

        init {
            val size = Dimension(JBUI.scale(30), JBUI.scale(16))
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(6)
                g2.color = if (isOn) ON_COLOR else OFF_COLOR
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                val inset = JBUI.scale(2)
                val knob = height - inset * 2
                val knobX = if (isOn) width - knob - inset else inset
                g2.color = Color.WHITE
                g2.fillRoundRect(knobX, inset, knob, knob, JBUI.scale(6), JBUI.scale(6))
            } finally {
                g2.dispose()
            }
        }

        companion object {
            private val ON_COLOR = JBColor(Color(0xE5, 0xEB, 0xF1), Color(0x4C, 0x50, 0x55))
            private val OFF_COLOR = JBColor(Color(0xC0, 0xC4, 0xC8), Color(0x66, 0x68, 0x6C))
        }
    }

    companion object {
        private val STATE_KEY = Key.create<Boolean>("codefocus.showLineNumbers.isOn")
    }
}
