package com.asynchrone.codefocus

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level user-configurable settings for the Code Focus plugin.
 *
 * Distinct from [CodeFocusToggleState], which holds per-file toggle pill
 * state. This service holds **project-wide preferences** the user edits via
 * [CodeFocusConfigurable] (Settings → Tools → Code Focus). Today that is the
 * substring list used by [ShowLoggingLinesToggle] to identify logging lines;
 * future settings live alongside.
 *
 * Stored in the project's workspace.xml so the values survive editor reopens,
 * project reopens, and PyCharm restarts.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CodeFocusSettingsState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class CodeFocusSettingsState : PersistentStateComponent<CodeFocusSettingsState.State> {
    class State {
        /** Plain (case-sensitive) substrings matched against PSI statement text. */
        var loggingPatterns: MutableList<String> = DEFAULT_LOGGING_PATTERNS.toMutableList()
    }

    private var state = State()
    private val listeners = CopyOnWriteArrayList<PatternsListener>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * The currently configured logging-line substrings. Each entry is a literal
     * substring (no regex); a line that contains any of them at any position is
     * considered a logging line. Falls back to [DEFAULT_LOGGING_PATTERNS] when
     * the user has cleared the list. The setter fires [PatternsListener]s
     * synchronously so subscribed UI components (e.g. open
     * `ShowLoggingLinesToggle`s) can re-apply immediately.
     */
    var loggingPatterns: List<String>
        get() = state.loggingPatterns.ifEmpty { DEFAULT_LOGGING_PATTERNS }
        set(value) {
            state.loggingPatterns = value.toMutableList()
            for (l in listeners) l.onPatternsChanged()
        }

    fun addPatternsListener(listener: PatternsListener) {
        listeners.add(listener)
    }

    fun removePatternsListener(listener: PatternsListener) {
        listeners.remove(listener)
    }

    fun interface PatternsListener {
        fun onPatternsChanged()
    }

    companion object {
        /**
         * Default substrings the Configurable seeds the Settings text area with via
         * "Restore defaults". Matching is plain `String.contains` (case-sensitive),
         * no regex — if any of these tokens appears anywhere in the statement text,
         * the line is folded.
         *
         *  - `logger` — covers `logger.x(...)`, `self.logger.x(...)`, `_logger`,
         *    `my_logger`, `local_logger`, every variable named after the logger.
         *  - `Logger` — covers `Logger`, `LoggerFactory`, `getLogger`, every
         *    PascalCase logger reference (class names, factory methods).
         *  - `logging` — covers `import logging` and `from logging import …`,
         *    plus any other use of the standard-library module name.
         */
        val DEFAULT_LOGGING_PATTERNS: List<String> =
            listOf(
                "logger",
                "Logger",
                "logging",
            )

        fun getInstance(project: Project): CodeFocusSettingsState = project.getService(CodeFocusSettingsState::class.java)
    }
}
