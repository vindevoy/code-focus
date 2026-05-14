package com.asynchrone.codefocus

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-level user-configurable settings for the Code Focus plugin.
 *
 * Distinct from [CodeFocusToggleState], which holds per-file toggle pill
 * state. This service holds **project-wide preferences** the user edits via
 * [CodeFocusConfigurable] (Settings → Tools → Code Focus). Today that is the
 * regex list used by [ShowLoggingLinesToggle] to identify logging lines;
 * future settings live alongside.
 *
 * Stored in the project's workspace.xml so the patterns survive editor
 * reopens, project reopens, and PyCharm restarts.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CodeFocusSettingsState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class CodeFocusSettingsState : PersistentStateComponent<CodeFocusSettingsState.State> {
    class State {
        /** Regex patterns matched against PSI statement text by Show Logging Lines. */
        var loggingPatterns: MutableList<String> = DEFAULT_LOGGING_PATTERNS.toMutableList()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * The currently configured logging-line patterns. Falls back to
     * [DEFAULT_LOGGING_PATTERNS] when the user has cleared the list.
     */
    var loggingPatterns: List<String>
        get() = state.loggingPatterns.ifEmpty { DEFAULT_LOGGING_PATTERNS }
        set(value) {
            state.loggingPatterns = value.toMutableList()
        }

    companion object {
        /**
         * Out-of-the-box patterns inherited from the original hardcoded list in
         * [ShowLoggingLinesToggle]. Each line of the user's configuration is one
         * pattern. The user can extend, replace, or restore these from the
         * Configurable's "Restore defaults" button.
         */
        val DEFAULT_LOGGING_PATTERNS: List<String> =
            listOf(
                """^\s*import\s+logging\b""",
                """^\s*from\s+logging\b""",
                """^\s*from\s+\S+\s+import\s+[^#\n]*\b[Ll]ogger\w*\b""",
                """^\s*import\s+[^#\n]*\b[Ll]ogger\w*\b""",
                """^\s*(?:\w+\.)*\w*[Ll]ogger\w*\s*=""",
                """^\s*(?:\w+\.)*\w*[Ll]ogger\w*\.\w+\(""",
            )

        fun getInstance(project: Project): CodeFocusSettingsState = project.getService(CodeFocusSettingsState::class.java)
    }
}
