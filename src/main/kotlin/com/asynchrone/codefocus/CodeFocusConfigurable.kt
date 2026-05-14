package com.asynchrone.codefocus

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * Project Settings → Tools → Code Focus.
 *
 * Today exposes one setting: the substrings Show Logging Lines uses to
 * identify lines as "logging-related". Each line in the text area is one
 * substring; blank lines are ignored. The "Restore defaults" button rewrites
 * the area with [CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS].
 *
 * Future settings live alongside in the same Configurable.
 */
class CodeFocusConfigurable(
    private val project: Project,
) : Configurable {
    private var patternsArea: JBTextArea? = null

    override fun getDisplayName(): String = CodeFocusBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val area =
            JBTextArea(8, 60).apply {
                font = JBUI.Fonts.create("Monospaced", JBUI.Fonts.label().size)
                lineWrap = true
                wrapStyleWord = false
                text = currentPatternsAsText()
            }
        patternsArea = area
        val scroll =
            JBScrollPane(area).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }
        return panel {
            row {
                text(CodeFocusBundle.message("settings.loggingPatterns.description"))
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row {
                cell(scroll)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
            row {
                button(CodeFocusBundle.message("settings.loggingPatterns.restoreDefaults")) {
                    area.text = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.joinToString("\n")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val area = patternsArea ?: return false
        return area.text.toPatternList() != CodeFocusSettingsState.getInstance(project).loggingPatterns
    }

    override fun apply() {
        val area = patternsArea ?: return
        CodeFocusSettingsState.getInstance(project).loggingPatterns = area.text.toPatternList()
    }

    override fun reset() {
        val area = patternsArea ?: return
        area.text = currentPatternsAsText()
    }

    override fun disposeUIResources() {
        patternsArea = null
    }

    private fun currentPatternsAsText(): String =
        CodeFocusSettingsState
            .getInstance(project)
            .loggingPatterns
            .joinToString("\n")

    private fun String.toPatternList(): List<String> = lines().map { it.trim() }.filter { it.isNotEmpty() }
}
