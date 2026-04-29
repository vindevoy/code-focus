package com.asynchrone.codefocus

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * Single notification panel that hosts both Code Focus toggles side by side
 * so they share one editor-notification row instead of stacking vertically.
 */
class CodeFocusToggleBar(
    editor: Editor? = null,
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(1))) {
    val showComments = ShowCommentsToggle(editor)
    val showBlankLines = ShowBlankLinesToggle(editor)

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 6)
        add(showComments)
        add(showBlankLines)
    }
}
