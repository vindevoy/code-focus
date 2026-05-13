package com.asynchrone.codefocus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
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
 * Slide-toggle pill for the "Show Logging Lines" switch.
 *
 * When OFF, hides Python statements that look like logging — imports of
 * `logging` or anything named `*logger*`, assignments to a `logger` symbol,
 * and method calls on a `logger` symbol. Detection runs against PSI
 * statements only, so occurrences inside comments or string literals are
 * not affected. Default ON, no on-disk modification.
 */
class ShowLoggingLinesToggle(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showLoggingLines.label"))

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
        if (!initial) applyToEditor()
    }

    private fun loadState(): Boolean? {
        val ed = editor ?: return null
        ed.getUserData(STATE_KEY)?.let { return it }
        val project = ed.project ?: return null
        val url = CodeFocusToggleState.fileUrl(ed) ?: return null
        return CodeFocusToggleState.getInstance(project).getShowLoggingLines(url)
    }

    private fun saveState(value: Boolean) {
        val ed = editor ?: return
        ed.putUserData(STATE_KEY, value)
        val project = ed.project ?: return
        val url = CodeFocusToggleState.fileUrl(ed) ?: return
        CodeFocusToggleState.getInstance(project).setShowLoggingLines(url, value)
    }

    private fun regionsFor(ed: Editor): MutableList<FoldRegion> {
        var list = ed.getUserData(REGIONS_KEY)
        if (list == null) {
            list = mutableListOf()
            ed.putUserData(REGIONS_KEY, list)
        }
        return list
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showLoggingLines.tooltip.on" else "toggle.showLoggingLines.tooltip.off"
        toolTipText = CodeFocusBundle.message(key)
        pill.toolTipText = toolTipText
        label.toolTipText = toolTipText
    }

    fun reApply() = applyToEditor()

    private fun applyToEditor() {
        val ed = editor ?: return
        val model = ed.foldingModel
        model.runBatchFoldingOperation {
            val regions = regionsFor(ed)
            for (r in regions) {
                if (r.isValid) model.removeFoldRegion(r)
            }
            regions.clear()
            if (pill.isOn) return@runBatchFoldingOperation
            val project = ed.project ?: return@runBatchFoldingOperation
            val psiFile =
                PsiDocumentManager.getInstance(project).getPsiFile(ed.document)
                    ?: return@runBatchFoldingOperation

            val ranges = mutableListOf<TextRange>()
            val statementTypes =
                arrayOf(
                    PyImportStatement::class.java,
                    PyFromImportStatement::class.java,
                    PyAssignmentStatement::class.java,
                    PyExpressionStatement::class.java,
                )
            for (stmt in PsiTreeUtil.findChildrenOfAnyType(psiFile, *statementTypes)) {
                if (looksLikeLogging(stmt)) ranges += stmt.textRange
            }

            for (range in ranges) {
                val (start, end) = expandRange(ed.document, range)
                if (start >= end) continue
                val region = model.addFoldRegion(start, end, "") ?: continue
                region.isExpanded = false
                regions.add(region)
            }
        }
    }

    private fun looksLikeLogging(stmt: PsiElement): Boolean {
        val text = stmt.text
        for (regex in LOGGING_REGEXES) {
            if (regex.containsMatchIn(text)) return true
        }
        return false
    }

    private fun expandRange(
        document: Document,
        range: TextRange,
    ): Pair<Int, Int> {
        val lineStart = document.getLineStartOffset(document.getLineNumber(range.startOffset))
        val prefix = document.getText(TextRange(lineStart, range.startOffset))
        if (prefix.any { !it.isWhitespace() }) {
            return range.startOffset to range.endOffset
        }
        val end = range.endOffset
        val withNewline =
            if (end < document.textLength && document.charsSequence[end] == '\n') {
                end + 1
            } else {
                end
            }
        return lineStart to withNewline
    }

    private class Pill : JComponent() {
        var isOn: Boolean = true

        init {
            val size = Dimension(JBUI.scale(36), JBUI.scale(20))
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
            private val ON_COLOR = Color(0x4FAEEF)
            private val OFF_COLOR = Color(0x9AA0A6)
        }
    }

    companion object {
        private val STATE_KEY = Key.create<Boolean>("codefocus.showLoggingLines.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showLoggingLines.regions")

        private val LOGGING_REGEXES =
            listOf(
                Regex("""^\s*import\s+logging\b"""),
                Regex("""^\s*from\s+logging\b"""),
                Regex("""^\s*from\s+\S+\s+import\s+[^#\n]*\b[Ll]ogger\w*\b"""),
                Regex("""^\s*import\s+[^#\n]*\b[Ll]ogger\w*\b"""),
                Regex("""^\s*\w*[Ll]ogger\w*\s*="""),
                Regex("""^\s*\w*[Ll]ogger\w*\.\w+\("""),
            )
    }
}
