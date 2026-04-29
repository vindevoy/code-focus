package com.asynchrone.codefocus

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

/**
 * Per-file persistence for the Show Comments / Show Blank Lines pills.
 *
 * Stored in the project's workspace.xml so the toggle state survives editor
 * reopens, project reopens, and PyCharm restarts. Keyed by the file URL so
 * different files keep independent settings.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CodeFocusToggleState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class CodeFocusToggleState : PersistentStateComponent<CodeFocusToggleState.State> {
    class State {
        var showCommentsByFile: MutableMap<String, Boolean> = mutableMapOf()
        var showBlankLinesByFile: MutableMap<String, Boolean> = mutableMapOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getShowComments(fileUrl: String): Boolean? = state.showCommentsByFile[fileUrl]

    fun setShowComments(
        fileUrl: String,
        value: Boolean,
    ) {
        state.showCommentsByFile[fileUrl] = value
    }

    fun getShowBlankLines(fileUrl: String): Boolean? = state.showBlankLinesByFile[fileUrl]

    fun setShowBlankLines(
        fileUrl: String,
        value: Boolean,
    ) {
        state.showBlankLinesByFile[fileUrl] = value
    }

    companion object {
        fun getInstance(project: Project): CodeFocusToggleState = project.getService(CodeFocusToggleState::class.java)

        fun fileUrl(editor: Editor?): String? {
            val ed = editor ?: return null
            val file = FileDocumentManager.getInstance().getFile(ed.document) ?: return null
            return file.url
        }
    }
}
