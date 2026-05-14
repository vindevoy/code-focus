package com.asynchrone.codefocus

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class CodeFocusToggleBarProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!file.extension.equals("py", ignoreCase = true)) return null
        return Function { editor -> if (editor is TextEditor) CodeFocusToggleBar(editor.editor) else null }
    }
}
