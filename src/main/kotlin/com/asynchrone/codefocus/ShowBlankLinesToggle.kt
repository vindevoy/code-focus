package com.asynchrone.codefocus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratable
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatementBase
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
 * Slide-toggle pill for the "Show Blank Lines" switch.
 *
 * Hides "decorative" blank lines while keeping the ones that PEP 8 requires
 * around top-level `def` and `class` definitions (two blank lines on each
 * side). Inside function bodies any blank line is fair game.
 */
class ShowBlankLinesToggle(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showBlankLines.label"))

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
        label.font = JBFont.small()
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
        return CodeFocusToggleState.getInstance(project).getShowBlankLines(url)
    }

    private fun saveState(value: Boolean) {
        val ed = editor ?: return
        ed.putUserData(STATE_KEY, value)
        val project = ed.project ?: return
        val url = CodeFocusToggleState.fileUrl(ed) ?: return
        CodeFocusToggleState.getInstance(project).setShowBlankLines(url, value)
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showBlankLines.tooltip.on" else "toggle.showBlankLines.tooltip.off"
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

            val protected = collectProtectedBlankLines(psiFile, ed.document)

            for (lineRange in findBlankLineRuns(ed.document)) {
                val (firstLine, lastLine) = lineRange
                val keep = (firstLine..lastLine).any { it in protected }
                if (keep) continue
                val start = ed.document.getLineStartOffset(firstLine)
                val end =
                    if (lastLine + 1 < ed.document.lineCount) {
                        ed.document.getLineStartOffset(lastLine + 1)
                    } else {
                        ed.document.getLineEndOffset(lastLine)
                    }
                if (start >= end) continue
                val region = model.addFoldRegion(start, end, "") ?: continue
                region.isExpanded = false
                regions.add(region)
            }
        }
    }

    private fun regionsFor(ed: Editor): MutableList<FoldRegion> {
        var list = ed.getUserData(REGIONS_KEY)
        if (list == null) {
            list = mutableListOf()
            ed.putUserData(REGIONS_KEY, list)
        }
        return list
    }

    private fun findBlankLineRuns(document: Document): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var inRun = false
        var runStart = 0
        for (line in 0 until document.lineCount) {
            val text =
                document.getText(
                    com.intellij.openapi.util.TextRange(
                        document.getLineStartOffset(line),
                        document.getLineEndOffset(line),
                    ),
                )
            val blank = text.all { it.isWhitespace() }
            if (blank && !inRun) {
                inRun = true
                runStart = line
            } else if (!blank && inRun) {
                runs.add(runStart to line - 1)
                inRun = false
            }
        }
        if (inRun) runs.add(runStart to document.lineCount - 1)
        return runs
    }

    private fun collectProtectedBlankLines(
        psiFile: PsiElement,
        document: Document,
    ): Set<Int> {
        val protected = mutableSetOf<Int>()
        val maxLine = document.lineCount - 1
        val all =
            PsiTreeUtil
                .findChildrenOfAnyType(psiFile, PyFunction::class.java, PyClass::class.java)
        for (def in all) {
            val buffer = if (def.parent === psiFile) 2 else 1
            val startLine = document.getLineNumber(effectiveStartOffset(def))
            val endLine = document.getLineNumber(def.textRange.endOffset)
            for (l in (startLine - buffer).coerceAtLeast(0) until startLine) protected += l
            for (l in (endLine + 1)..(endLine + buffer).coerceAtMost(maxLine)) protected += l
        }
        var lastImportEnd = -1
        for (child in psiFile.children) {
            if (child is PyImportStatementBase) {
                val end = document.getLineNumber(child.textRange.endOffset)
                if (end > lastImportEnd) lastImportEnd = end
            }
        }
        if (lastImportEnd >= 0) {
            for (l in (lastImportEnd + 1)..(lastImportEnd + 2).coerceAtMost(maxLine)) protected += l
        }
        return protected
    }

    private fun effectiveStartOffset(def: PsiElement): Int {
        val base = def.textRange.startOffset
        if (def is PyDecoratable) {
            val list = def.decoratorList
            if (list != null) return minOf(base, list.textRange.startOffset)
        }
        return base
    }

    private class Pill : JComponent() {
        var isOn: Boolean = true

        init {
            val size = Dimension(JBUI.scale(24), JBUI.scale(12))
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(2)
                g2.color = if (isOn) ON_COLOR else OFF_COLOR
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                val knob = JBUI.scale(8)
                val knobInset = (height - knob) / 2
                val knobX = if (isOn) width - knob - knobInset else knobInset
                val knobY = (height - knob) / 2
                g2.color = JBColor.foreground()
                g2.fillRoundRect(knobX, knobY, knob, knob, arc, arc)
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
        private val STATE_KEY = Key.create<Boolean>("codefocus.showBlankLines.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showBlankLines.regions")
    }
}
