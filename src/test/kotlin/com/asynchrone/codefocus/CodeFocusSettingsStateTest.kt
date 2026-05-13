package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeFocusSettingsStateTest {
    @Test
    fun `default logging patterns include the canonical six rules`() {
        val patterns = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        assertEquals(6, patterns.size, "Expected the six original rules from ShowLoggingLinesToggle")
        assertTrue(patterns.any { it.contains("import\\s+logging") }, "Expected an `import logging` rule")
        assertTrue(patterns.any { it.contains("[Ll]ogger") }, "Expected at least one logger-symbol rule")
    }

    @Test
    fun `default patterns compile as valid regexes`() {
        for (p in CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS) {
            // Throws PatternSyntaxException if invalid; the assertion is "we got here"
            Regex(p)
        }
    }

    @Test
    fun `state getter falls back to defaults when the user list is empty`() {
        val state = CodeFocusSettingsState()
        state.loggingPatterns = emptyList()
        assertEquals(CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS, state.loggingPatterns)
    }

    @Test
    fun `state getter returns the user list when it is non-empty`() {
        val state = CodeFocusSettingsState()
        val custom = listOf("""^foo\b""", """^bar\b""")
        state.loggingPatterns = custom
        assertEquals(custom, state.loggingPatterns)
        assertFalse(CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS == state.loggingPatterns)
    }
}
