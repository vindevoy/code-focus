package com.asynchrone.codefocus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Pill-shaped "Check" button. Shipped as the eighth child of [CodeFocusToggleBar],
 * to the right of [FormatButton].
 *
 * Visibility is gated on `ruff` being reachable for the project — same lookup
 * order as [FormatButton] (`<project>/.venv/bin/ruff` → PATH), so the two
 * buttons appear / disappear together.
 *
 * Clicking saves the editor's document, invokes `<ruff> check --fix <file>` on
 * a background thread, and on completion refreshes the VirtualFile so the
 * editor picks up any auto-fixes. If ruff exits non-zero (i.e. there are
 * issues it could not auto-fix), its captured output is shown in a modal
 * warning dialog so the user can act on the remaining diagnostics. A clean
 * exit ("All checks passed") is silent — no popup.
 *
 * `ruffResolver` is injectable so tests can construct the button in either
 * state without depending on whether ruff happens to be installed on the
 * test runner.
 */
class CheckButton(
    private val editor: Editor? = null,
    ruffResolver: () -> String? = { FormatButton.defaultResolveRuff(editor) },
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), JBUI.scale(1))) {
    private val label = JLabel(CodeFocusBundle.message("button.check.label"))
    private val resolvedRuff: String? = ruffResolver()

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 2)

        if (resolvedRuff != null) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = CodeFocusBundle.message("button.check.tooltip")
            label.border = JBUI.Borders.empty(4, 10)
            label.foreground = JBColor.foreground()
            add(BackgroundPill(label))

            val click =
                object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        runCheck()
                    }
                }
            addMouseListener(click)
            label.addMouseListener(click)
        } else {
            isVisible = false
        }
    }

    private fun runCheck() {
        val ed = editor ?: return
        val ruff = resolvedRuff ?: return
        val file = FileDocumentManager.getInstance().getFile(ed.document) ?: return
        val app = ApplicationManager.getApplication()
        app.invokeLater {
            app.runWriteAction {
                FileDocumentManager.getInstance().saveDocument(ed.document)
            }
            app.executeOnPooledThread {
                val proc =
                    runCatching {
                        ProcessBuilder(ruff, "check", "--fix", file.path)
                            .redirectErrorStream(true)
                            .start()
                    }.getOrNull() ?: return@executeOnPooledThread
                val output =
                    proc.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                val exit = proc.waitFor()
                app.invokeLater {
                    file.refresh(false, false)
                    if (exit != 0 && output.isNotBlank()) {
                        Messages.showMessageDialog(
                            ed.project,
                            output,
                            CodeFocusBundle.message("button.check.dialog.title"),
                            Messages.getWarningIcon(),
                        )
                    }
                }
            }
        }
    }

    private class BackgroundPill(
        private val inner: JLabel,
    ) : JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)) {
        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(inner)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = BG_COLOR
                val arc = JBUI.scale(6)
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }

        companion object {
            // Same theme-aware tone as the Re-Apply / Format buttons.
            private val BG_COLOR = JBColor(Color(0xE5, 0xEB, 0xF1), Color(0x4C, 0x50, 0x55))
        }
    }
}
