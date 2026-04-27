package com.asynchrone.codefocus

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * Adds the "Show Comments" slide-toggle above the editor for `.py` files.
 *
 * Returns null for non-Python files so the platform installs nothing on them.
 */
class ShowCommentsToggleProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!isPython(file)) return null
        return Function { editor -> if (editor is TextEditor) ShowCommentsToggle(editor.editor) else null }
    }

    private fun isPython(file: VirtualFile): Boolean = file.extension.equals("py", ignoreCase = true)
}
