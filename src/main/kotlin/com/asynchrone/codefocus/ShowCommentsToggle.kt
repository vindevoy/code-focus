package com.asynchrone.codefocus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStatement
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
            saveState(value)
            applyToEditor()
        }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 2)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.font = JBFont.label()
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
        return CodeFocusToggleState.getInstance(project).getShowComments(url)
    }

    private fun saveState(value: Boolean) {
        val ed = editor ?: return
        ed.putUserData(STATE_KEY, value)
        val project = ed.project ?: return
        val url = CodeFocusToggleState.fileUrl(ed) ?: return
        CodeFocusToggleState.getInstance(project).setShowComments(url, value)
    }

    private fun updateTooltip() {
        val key = if (pill.isOn) "toggle.showComments.tooltip.on" else "toggle.showComments.tooltip.off"
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
                if (!isCommentOnlyRegion(existing, psiFile, ed.document)) continue
                existing.isExpanded = pill.isOn
            }

            if (pill.isOn) return@runBatchFoldingOperation

            val ranges = mutableListOf<TextRange>()
            for (comment in PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)) {
                ranges += comment.textRange
            }
            for (stmt in PsiTreeUtil.findChildrenOfType(psiFile, PyExpressionStatement::class.java)) {
                if (stmt.expression is PyStringLiteralExpression) {
                    ranges += stmt.textRange
                }
            }
            ranges.sortBy { it.startOffset }

            var previousFoldEnd = 0

            for (range in ranges) {
                val (start, end) = FoldExpansion.expand(ed.document, range, previousFoldEnd)
                if (start >= end) continue
                val coveredByCollapsed =
                    model.allFoldRegions.any {
                        it.isValid &&
                            !it.isExpanded &&
                            it.startOffset <= start &&
                            it.endOffset >= end
                    }
                if (coveredByCollapsed) continue
                val region = model.addFoldRegion(start, end, "")
                if (region != null) {
                    region.isExpanded = false
                    regions.add(region)
                    previousFoldEnd = end
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

    private fun isCommentOnlyRegion(
        fold: FoldRegion,
        psiFile: PsiElement,
        document: Document,
    ): Boolean {
        val foldEndLine = document.getLineNumber((fold.endOffset - 1).coerceAtLeast(fold.startOffset))
        val foldStartLine = document.getLineNumber(fold.startOffset)
        if (foldEndLine - foldStartLine > 200) return false
        val foldRange = TextRange(fold.startOffset, fold.endOffset)
        var hasContent = false
        for (stmt in PsiTreeUtil.findChildrenOfType(psiFile, PyStatement::class.java)) {
            if (!foldRange.contains(stmt.textRange.startOffset)) continue
            if (stmt is PyExpressionStatement && stmt.expression is PyStringLiteralExpression) {
                hasContent = true
                continue
            }
            return false
        }
        if (!hasContent) {
            for (comment in PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)) {
                if (foldRange.contains(comment.textRange.startOffset)) {
                    hasContent = true
                    break
                }
            }
        }
        return hasContent
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
        private val STATE_KEY = Key.create<Boolean>("codefocus.showComments.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showComments.regions")
    }
}
