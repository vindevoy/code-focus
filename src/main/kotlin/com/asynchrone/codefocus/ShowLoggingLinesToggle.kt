package com.asynchrone.codefocus

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
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
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

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
            LOG.warn("[CodeFocus] isOn setter: $isOn -> $value (editor=$editor)")
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

        val project = editor?.project
        if (project != null) {
            val state = CodeFocusSettingsState.getInstance(project)
            val patternsListener = CodeFocusSettingsState.PatternsListener { applyToEditor() }
            state.addPatternsListener(patternsListener)
            addAncestorListener(
                object : AncestorListener {
                    override fun ancestorRemoved(event: AncestorEvent) {
                        state.removePatternsListener(patternsListener)
                    }

                    override fun ancestorAdded(event: AncestorEvent) {}

                    override fun ancestorMoved(event: AncestorEvent) {}
                },
            )
        }
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
        LOG.warn("[CodeFocus] applyToEditor: ENTER (pill.isOn=${pill.isOn}, editor=$editor)")
        val ed =
            editor ?: run {
                LOG.warn("[CodeFocus] applyToEditor: editor is null, returning")
                return
            }
        val model = ed.foldingModel
        val needles = loggingNeedles()
        LOG.warn("[CodeFocus] applyToEditor: ${needles.size} active substrings: $needles")
        model.runBatchFoldingOperation {
            // Restore IDE-managed folds we previously collapsed.
            val toggled = toggledFor(ed)
            LOG.warn("[CodeFocus] applyToEditor: re-expanding ${toggled.count { it.isValid }} previously-collapsed IDE folds")
            for (r in toggled) {
                if (r.isValid) r.isExpanded = true
            }
            toggled.clear()

            // Remove folds we ourselves created.
            val regions = regionsFor(ed)
            LOG.warn("[CodeFocus] applyToEditor: removing ${regions.count { it.isValid }} previously-created folds")
            for (r in regions) {
                if (r.isValid) model.removeFoldRegion(r)
            }
            regions.clear()

            if (pill.isOn) {
                LOG.warn("[CodeFocus] applyToEditor: pill is ON, exiting (no folds added)")
                return@runBatchFoldingOperation
            }
            val project =
                ed.project ?: run {
                    LOG.warn("[CodeFocus] applyToEditor: editor.project is null, exiting")
                    return@runBatchFoldingOperation
                }
            val psiFile =
                PsiDocumentManager.getInstance(project).getPsiFile(ed.document) ?: run {
                    LOG.warn("[CodeFocus] applyToEditor: psiFile is null, exiting")
                    return@runBatchFoldingOperation
                }
            LOG.warn("[CodeFocus] applyToEditor: psiFile=${psiFile.name}")

            val ranges = mutableListOf<TextRange>()
            val statementTypes =
                arrayOf(
                    PyImportStatement::class.java,
                    PyFromImportStatement::class.java,
                    PyAssignmentStatement::class.java,
                    PyExpressionStatement::class.java,
                )
            val allStatements = PsiTreeUtil.findChildrenOfAnyType(psiFile, *statementTypes)
            LOG.warn("[CodeFocus] applyToEditor: PSI walk found ${allStatements.size} statements of target types")
            for (stmt in allStatements) {
                val text = stmt.text
                val preview = text.lineSequence().firstOrNull()?.take(80) ?: ""
                val matched = looksLikeLogging(stmt)
                LOG.warn(
                    "[CodeFocus]   stmt class=${stmt.javaClass.simpleName} " +
                        "range=${stmt.textRange} matched=$matched text=`$preview`",
                )
                if (matched) ranges += stmt.textRange
            }
            LOG.warn("[CodeFocus] applyToEditor: ${ranges.size} statements matched the regex set")

            ranges.sortBy { it.startOffset }

            var added = 0
            var collapsed = 0
            var alreadyCollapsed = 0
            var failed = 0
            var previousFoldEnd = 0
            for (range in ranges) {
                val (lineStart, lineEnd) = FoldExpansion.expand(ed.document, range, previousFoldEnd)
                if (lineStart >= lineEnd) {
                    LOG.warn("[CodeFocus]   skipping empty range $range -> ($lineStart,$lineEnd)")
                    continue
                }

                // Step 1: look for an existing fold region at the line range or the statement
                // range. PyCharm pre-creates folds for many import / call statements; if we find
                // one, just toggle its expansion instead of trying to add a duplicate.
                val existing =
                    model.allFoldRegions.firstOrNull {
                        it.isValid &&
                            (
                                (it.startOffset == lineStart && it.endOffset == lineEnd) ||
                                    (it.startOffset == range.startOffset && it.endOffset == range.endOffset) ||
                                    (it.startOffset == lineStart && it.endOffset == range.endOffset)
                            )
                    }
                if (existing != null) {
                    if (existing.isExpanded) {
                        existing.isExpanded = false
                        toggled.add(existing)
                        collapsed++
                        LOG.warn(
                            "[CodeFocus]   collapsed existing IDE fold " +
                                "[${existing.startOffset},${existing.endOffset}]",
                        )
                    } else {
                        alreadyCollapsed++
                        LOG.warn(
                            "[CodeFocus]   existing IDE fold already collapsed " +
                                "[${existing.startOffset},${existing.endOffset}] (no action)",
                        )
                    }
                    previousFoldEnd = existing.endOffset
                    continue
                }

                // Step 2: no existing fold matches. Try to add one. Try the line-with-newline
                // range first, then without the trailing newline (lets the new fold sit
                // strictly inside an enclosing block fold whose end is at the same offset).
                val region =
                    model.addFoldRegion(lineStart, lineEnd, "")
                        ?: model.addFoldRegion(lineStart, range.endOffset, "")
                        ?: model.addFoldRegion(range.startOffset, range.endOffset, "")
                if (region == null) {
                    val overlapping =
                        model.allFoldRegions.filter {
                            it.isValid && (it.startOffset < lineEnd && it.endOffset > lineStart)
                        }
                    LOG.warn(
                        "[CodeFocus]   addFoldRegion FAILED for ($lineStart,$lineEnd). " +
                            "Overlapping existing folds: ${overlapping.size}: " +
                            overlapping.joinToString(" | ") {
                                "[${it.startOffset},${it.endOffset}, expanded=${it.isExpanded}]"
                            },
                    )
                    failed++
                    continue
                }
                region.isExpanded = false
                regions.add(region)
                previousFoldEnd = region.endOffset
                added++
                LOG.warn(
                    "[CodeFocus]   addFoldRegion OK: " +
                        "[${region.startOffset},${region.endOffset}], isExpanded=false",
                )
            }
            LOG.warn(
                "[CodeFocus] applyToEditor: DONE collapsed=$collapsed alreadyCollapsed=$alreadyCollapsed " +
                    "added=$added failed=$failed " +
                    "(${regions.size} created folds, ${toggled.size} IDE folds toggled)",
            )
        }
    }

    private fun toggledFor(ed: Editor): MutableList<FoldRegion> {
        var list = ed.getUserData(TOGGLED_KEY)
        if (list == null) {
            list = mutableListOf()
            ed.putUserData(TOGGLED_KEY, list)
        }
        return list
    }

    private fun looksLikeLogging(stmt: PsiElement): Boolean {
        val text = stmt.text
        for (needle in loggingNeedles()) {
            if (needle.isNotEmpty() && text.contains(needle)) return true
        }
        return false
    }

    private fun loggingNeedles(): List<String> {
        val project = editor?.project ?: return CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        return CodeFocusSettingsState.getInstance(project).loggingPatterns
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
        private val STATE_KEY = Key.create<Boolean>("codefocus.showLoggingLines.isOn")
        private val REGIONS_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showLoggingLines.regions")
        private val TOGGLED_KEY = Key.create<MutableList<FoldRegion>>("codefocus.showLoggingLines.toggled")
        private val LOG = Logger.getInstance(ShowLoggingLinesToggle::class.java)
    }
}
