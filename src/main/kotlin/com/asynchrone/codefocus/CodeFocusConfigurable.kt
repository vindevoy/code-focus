package com.asynchrone.codefocus

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Project Settings → Tools → Code Focus.
 *
 * Today exposes one setting: the regex patterns Show Logging Lines uses to
 * identify lines as "logging-related". Each line in the text area is one
 * pattern; blank lines are ignored. The "Restore defaults" button rewrites
 * the area with [CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS].
 *
 * Future settings live alongside in the same Configurable.
 */
class CodeFocusConfigurable(
    private val project: Project,
) : Configurable {
    private var patternsArea: JBTextArea? = null
    private var rootPanel: DialogPanel? = null

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

        val scroll = JBScrollPane(area)
        val description =
            JBLabel(CodeFocusBundle.message("settings.loggingPatterns.description")).apply {
                border = JBUI.Borders.emptyBottom(8)
                setAllowAutoWrapping(true)
            }

        val restoreButton =
            JButton(CodeFocusBundle.message("settings.loggingPatterns.restoreDefaults")).apply {
                addActionListener {
                    area.text = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.joinToString("\n")
                }
            }
        val buttonRow =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                border = JBUI.Borders.emptyTop(8)
                add(restoreButton)
            }

        val panel =
            FormBuilder
                .createFormBuilder()
                .addComponent(description)
                .addLabeledComponent(JBLabel(CodeFocusBundle.message("settings.loggingPatterns.label")), scroll, 1, true)
                .addComponent(buttonRow)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        val dialog =
            DialogPanel().apply {
                layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
                border = JBUI.Borders.empty(10)
                add(panel)
            }
        rootPanel = dialog
        return dialog
    }

    override fun isModified(): Boolean {
        val area = patternsArea ?: return false
        return area.text.toPatternList() != CodeFocusSettingsState.getInstance(project).loggingPatterns
    }

    override fun apply() {
        val area = patternsArea ?: return
        CodeFocusSettingsState.getInstance(project).loggingPatterns = area.text.toPatternList()
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    override fun reset() {
        val area = patternsArea ?: return
        area.text = currentPatternsAsText()
    }

    override fun disposeUIResources() {
        patternsArea = null
        rootPanel = null
    }

    private fun currentPatternsAsText(): String =
        CodeFocusSettingsState
            .getInstance(project)
            .loggingPatterns
            .joinToString("\n")

    private fun String.toPatternList(): List<String> = lines().map { it.trim() }.filter { it.isNotEmpty() }
}
