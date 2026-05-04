package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * Single notification panel that hosts every Code Focus toggle and the
 * Re-Apply button side by side so they share one editor-notification row
 * instead of stacking vertically.
 */
class CodeFocusToggleBar(
    editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(1))) {
    val showComments = ShowCommentsToggle(editor)
    val showBlankLines = ShowBlankLinesToggle(editor)
    val showLineNumbers = ShowLineNumbersToggle(editor)
    val showLoggingLines = ShowLoggingLinesToggle(editor)
    val showImports = ShowImportsToggle(editor)
    val reApplyButton = ReApplyButton(this)

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 6)
        add(showComments)
        add(showBlankLines)
        add(showLineNumbers)
        add(showLoggingLines)
        add(showImports)
        add(reApplyButton)
    }

    /**
     * Re-runs every toggle's hide logic against the editor's current PSI,
     * picking up any new comment / docstring / blank line / import / logging
     * line the developer typed since the last toggle change. Idempotent;
     * does nothing for toggles that are currently in their visible-by-default
     * state.
     */
    fun reApply() {
        showComments.reApply()
        showBlankLines.reApply()
        showLineNumbers.reApply()
        showLoggingLines.reApply()
        showImports.reApply()
    }
}
