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
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyStatement
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
 * Slide-toggle pill for the "Show Imports" switch.
 *
 * When OFF, hides every Python `import` and `from … import …` statement via
 * empty-placeholder fold regions. Default ON, state persisted on the
 * editor's user data, no on-disk modification.
 */
class ShowImportsToggle(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showImports.label"))

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
        return CodeFocusToggleState.getInstance(project).getShowImports(url)
    }

    private fun saveState(value: Boolean) {
        val ed = editor ?: return
        ed.putUserData(STATE_KEY, value)
        val project = ed.project ?: return
        val url = CodeFocusToggleState.fileUrl(ed) ?: return
        CodeFocusToggleState.getInstance(project).setShowImports(url, value)
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
        val key = if (pill.isOn) "toggle.showImports.tooltip.on" else "toggle.showImports.tooltip.off"
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

            val project = ed.project ?: return@runBatchFoldingOperation
            val psiFile =
                PsiDocumentManager.getInstance(project).getPsiFile(ed.document)
                    ?: return@runBatchFoldingOperation

            for (existing in model.allFoldRegions.toList()) {
                if (!existing.isValid) continue
                if (!isImportOnlyRegion(existing, psiFile, ed.document)) continue
                existing.isExpanded = pill.isOn
            }

            if (pill.isOn) return@runBatchFoldingOperation

            for (group in topLevelImportGroups(psiFile)) {
                val (start, end) = importGroupRange(ed.document, group)
                if (start >= end) continue
                if (alreadyCovered(model, start, end)) continue
                val region = model.addFoldRegion(start, end, "") ?: continue
                region.isExpanded = false
                regions.add(region)
            }

            for (stmt in PsiTreeUtil.findChildrenOfAnyType(
                psiFile,
                PyImportStatement::class.java,
                PyFromImportStatement::class.java,
            )) {
                if (stmt.parent === psiFile) continue
                val (start, end) = expandRange(ed.document, stmt.textRange)
                if (start >= end) continue
                if (alreadyCovered(model, start, end)) continue
                val region = model.addFoldRegion(start, end, "") ?: continue
                region.isExpanded = false
                regions.add(region)
            }
        }
    }

    private fun topLevelImportGroups(psiFile: PsiElement): List<List<PyImportStatementBase>> {
        val groups = mutableListOf<MutableList<PyImportStatementBase>>()
        var current: MutableList<PyImportStatementBase>? = null
        for (child in psiFile.children) {
            if (child is PyImportStatementBase) {
                if (current == null) current = mutableListOf()
                current.add(child)
            } else if (child is PyStatement) {
                current?.let { groups.add(it) }
                current = null
            }
        }
        current?.let { groups.add(it) }
        return groups
    }

    private fun importGroupRange(
        document: com.intellij.openapi.editor.Document,
        group: List<PyImportStatementBase>,
    ): Pair<Int, Int> {
        val first = group.first()
        val last = group.last()
        val startLine = document.getLineNumber(first.textRange.startOffset)
        var endLine = document.getLineNumber((last.textRange.endOffset - 1).coerceAtLeast(first.textRange.startOffset))
        while (endLine + 1 < document.lineCount) {
            val nextLine = endLine + 1
            val text =
                document.getText(
                    TextRange(
                        document.getLineStartOffset(nextLine),
                        document.getLineEndOffset(nextLine),
                    ),
                )
            if (text.all { it.isWhitespace() }) endLine = nextLine else break
        }
        val start = document.getLineStartOffset(startLine)
        val end =
            if (endLine + 1 < document.lineCount) {
                document.getLineStartOffset(endLine + 1)
            } else {
                document.getLineEndOffset(endLine)
            }
        return start to end
    }

    private fun alreadyCovered(
        model: com.intellij.openapi.editor.FoldingModel,
        start: Int,
        end: Int,
    ): Boolean =
        model.allFoldRegions.any {
            it.isValid &&
                !it.isExpanded &&
                it.startOffset <= start &&
                it.endOffset >= end
        }

    private fun isImportOnlyRegion(
        fold: FoldRegion,
        psiFile: PsiElement,
        document: com.intellij.openapi.editor.Document,
    ): Boolean {
        val foldStartLine = document.getLineNumber(fold.startOffset)
        val foldEndLine = document.getLineNumber((fold.endOffset - 1).coerceAtLeast(fold.startOffset))
        if (foldEndLine - foldStartLine > 200) return false
        val foldRange = TextRange(fold.startOffset, fold.endOffset)
        var hasImport = false
        for (stmt in PsiTreeUtil.findChildrenOfType(psiFile, PyStatement::class.java)) {
            if (!foldRange.contains(stmt.textRange.startOffset)) continue
            if (stmt is PyImportStatementBase) {
                hasImport = true
                continue
            }
            return false
        }
        return hasImport
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

    companion object {
        private val STATE_KEY = Key.create<Boolean>("codefocus.showImports.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showImports.regions")
    }
}
