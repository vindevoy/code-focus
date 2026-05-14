package com.asynchrone.codefocus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Pill-shaped "Format" button. Shipped as the seventh child of [CodeFocusToggleBar].
 *
 * Visible only when `ruff` is reachable for the project. Resolution order
 * (probed once at construction):
 *   1. `<project-base>/.venv/bin/ruff` (Linux/macOS) or
 *      `<project-base>/.venv/Scripts/ruff.exe` (Windows) — the canonical
 *      project venv set up by `resources/python/setup-uv-env.fish` (issue #41).
 *   2. Whatever `which ruff` resolves to on the JVM's PATH.
 *
 * Whichever path is found is stored verbatim and re-used at click time, so
 * the button works whether ruff was installed system-wide or only inside the
 * project venv. Clicking saves the editor's document, invokes
 * `<resolved-ruff> format <file>` on a background thread, then refreshes the
 * VirtualFile so the editor picks up the reformatted content.
 *
 * `ruffResolver` is injectable so tests can construct the button in either
 * state without depending on whether ruff happens to be installed on the
 * test runner.
 */
class FormatButton(
    private val editor: Editor? = null,
    ruffResolver: () -> String? = { defaultResolveRuff(editor) },
) : JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), JBUI.scale(1))) {
    private val label = JLabel(CodeFocusBundle.message("button.format.label"))
    private val resolvedRuff: String? = ruffResolver()

    init {
        isOpaque = false
        border = JBUI.Borders.empty(1, 2)

        if (resolvedRuff != null) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = CodeFocusBundle.message("button.format.tooltip")
            label.border = JBUI.Borders.empty(4, 10)
            label.foreground = JBColor.foreground()
            label.font = JBFont.small()
            add(BackgroundPill(label))

            val click =
                object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        runFormat()
                    }
                }
            addMouseListener(click)
            label.addMouseListener(click)
        } else {
            isVisible = false
        }
    }

    private fun runFormat() {
        val ed = editor ?: return
        val ruff = resolvedRuff ?: return
        val file = FileDocumentManager.getInstance().getFile(ed.document) ?: return
        val app = ApplicationManager.getApplication()
        app.invokeLater {
            app.runWriteAction {
                FileDocumentManager.getInstance().saveDocument(ed.document)
            }
            app.executeOnPooledThread {
                runCatching {
                    ProcessBuilder(ruff, "format", file.path)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
                app.invokeLater {
                    file.refresh(false, false)
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
            // Same theme-aware tone as the Re-Apply button.
            private val BG_COLOR = JBColor(Color(0xE5, 0xEB, 0xF1), Color(0x4C, 0x50, 0x55))
        }
    }

    companion object {
        fun defaultResolveRuff(editor: Editor?): String? {
            val basePath = editor?.project?.basePath
            if (basePath != null) {
                for (rel in listOf(".venv/bin/ruff", ".venv/Scripts/ruff.exe")) {
                    val candidate = File(basePath, rel)
                    if (candidate.exists() && candidate.canExecute()) {
                        return candidate.absolutePath
                    }
                }
            }
            return try {
                val proc =
                    ProcessBuilder("which", "ruff")
                        .redirectErrorStream(true)
                        .start()
                if (proc.waitFor() == 0) {
                    val out =
                        proc.inputStream
                            .bufferedReader()
                            .readText()
                            .trim()
                    out.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
