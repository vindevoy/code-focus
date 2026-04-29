package com.asynchrone.codefocus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStringLiteralExpression
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
 * Slide-toggle pill for the "Show Comments" switch.
 *
 * On is the default — comments stay visible. Off folds every Python comment
 * with an empty placeholder so it disappears from view without touching the
 * file. State is persisted on the [Editor] via [STATE_KEY] so a re-created
 * toggle picks up the previous choice.
 */
class ShowCommentsToggle(
    private val editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(1))) {
    private val pill = Pill()
    private val label = JLabel(CodeFocusBundle.message("toggle.showComments.label"))

    var isOn: Boolean
        get() = pill.isOn
        set(value) {
            if (pill.isOn == value) return
            pill.isOn = value
            pill.repaint()
            updateTooltip()
            editor?.putUserData(STATE_KEY, value)
            applyToEditor()
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

        val initial = editor?.getUserData(STATE_KEY) ?: true
        pill.isOn = initial
        updateTooltip()
        if (!initial) applyToEditor()
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showComments.tooltip.on" else "toggle.showComments.tooltip.off"
        toolTipText = CodeFocusBundle.message(key)
        pill.toolTipText = toolTipText
        label.toolTipText = toolTipText
    }

    private fun applyToEditor() {
        val ed = editor ?: return
        val model = ed.foldingModel
        model.runBatchFoldingOperation {
            val regions = regionsFor(ed)
            for (r in regions) {
                if (r.isValid) model.removeFoldRegion(r)
            }
            regions.clear()
            val collapsed = collapsedFor(ed)
            for (r in collapsed) {
                if (r.isValid) r.isExpanded = true
            }
            collapsed.clear()

            if (pill.isOn) return@runBatchFoldingOperation

            val project = ed.project ?: return@runBatchFoldingOperation
            val psiFile =
                PsiDocumentManager.getInstance(project).getPsiFile(ed.document)
                    ?: return@runBatchFoldingOperation

            val ranges = mutableListOf<TextRange>()
            for (comment in PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)) {
                ranges += comment.textRange
            }
            for (stmt in PsiTreeUtil.findChildrenOfType(psiFile, PyExpressionStatement::class.java)) {
                if (stmt.expression is PyStringLiteralExpression) {
                    ranges += stmt.textRange
                }
            }

            for (range in ranges) {
                val (start, end) = expandRange(ed.document, range)
                if (start >= end) continue
                val region = model.addFoldRegion(start, end, "")
                if (region != null) {
                    region.isExpanded = false
                    regions.add(region)
                } else {
                    val existing =
                        model.allFoldRegions
                            .filter {
                                it.isValid &&
                                    it.startOffset <= start &&
                                    it.endOffset >= end &&
                                    it !in collapsed
                            }.minByOrNull { it.endOffset - it.startOffset }
                    if (existing != null && existing.isExpanded && isCommentSizedFold(ed.document, existing, start, end)) {
                        existing.isExpanded = false
                        collapsed.add(existing)
                    }
                }
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

    private fun collapsedFor(ed: Editor): MutableList<FoldRegion> {
        var list = ed.getUserData(COLLAPSED_KEY)
        if (list == null) {
            list = mutableListOf()
            ed.putUserData(COLLAPSED_KEY, list)
        }
        return list
    }

    private fun isCommentSizedFold(
        document: Document,
        fold: FoldRegion,
        targetStart: Int,
        targetEnd: Int,
    ): Boolean {
        val foldStartLine = document.getLineNumber(fold.startOffset)
        val foldEndLine = document.getLineNumber((fold.endOffset - 1).coerceAtLeast(fold.startOffset))
        val targetStartLine = document.getLineNumber(targetStart)
        val targetEndLine = document.getLineNumber((targetEnd - 1).coerceAtLeast(targetStart))
        val paddingLines = (foldEndLine - targetEndLine) + (targetStartLine - foldStartLine)
        return paddingLines <= 4
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
        private val STATE_KEY = Key.create<Boolean>("codefocus.showComments.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showComments.regions")
        private val COLLAPSED_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showComments.collapsed")
    }
}
