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

    @Test
    fun `patterns listener fires synchronously on every setter call`() {
        val state = CodeFocusSettingsState()
        var firedCount = 0
        val listener = CodeFocusSettingsState.PatternsListener { firedCount++ }
        state.addPatternsListener(listener)
        state.loggingPatterns = listOf("""^foo\b""")
        state.loggingPatterns = listOf("""^bar\b""")
        state.loggingPatterns = listOf("""^foo\b""")
        assertEquals(3, firedCount, "Listener must fire on every setter call, even when the value is unchanged")
    }

    @Test
    fun `removed patterns listener does not fire`() {
        val state = CodeFocusSettingsState()
        var firedCount = 0
        val listener = CodeFocusSettingsState.PatternsListener { firedCount++ }
        state.addPatternsListener(listener)
        state.removePatternsListener(listener)
        state.loggingPatterns = listOf("""^foo\b""")
        assertEquals(0, firedCount)
    }

    @Test
    fun `multiple listeners all fire in registration order`() {
        val state = CodeFocusSettingsState()
        val order = mutableListOf<String>()
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "a" })
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "b" })
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "c" })
        state.loggingPatterns = listOf("""^foo\b""")
        assertEquals(listOf("a", "b", "c"), order)
    }

    @Test
    fun `default patterns match unprefixed logger calls with any leading whitespace`() {
        val regexes = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.map { Regex(it) }
        val samples =
            listOf(
                """logger.warning("x")""",
                """    logger.warning("x")""",
                "\tlogger.warning(\"x\")",
                "\t\tlogger.warning(\"x\")",
                """            logger.warning("x")""",
            )
        for (s in samples) {
            assertTrue(regexes.any { it.containsMatchIn(s) }, "Expected at least one default pattern to match `$s`")
        }
    }

    @Test
    fun `default patterns match prefixed logger calls (self, module, chained)`() {
        val regexes = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.map { Regex(it) }
        val samples =
            listOf(
                """self.logger.warning("x")""",
                """    self.logger.warning("x")""",
                "\t\tself.logger.warning(\"x\")",
                """module.sub.logger.warning("x")""",
                """self.my_logger.info("x")""",
                """_logger.warning("x")""",
                """my_logger.debug("x")""",
            )
        for (s in samples) {
            assertTrue(regexes.any { it.containsMatchIn(s) }, "Expected at least one default pattern to match `$s`")
        }
    }

    @Test
    fun `default patterns match logger assignments with and without prefixes`() {
        val regexes = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.map { Regex(it) }
        val samples =
            listOf(
                """logger = logging.getLogger(__name__)""",
                """    logger = logging.getLogger(__name__)""",
                """self.logger = logging.getLogger("X")""",
                """    self._logger = logging.getLogger(__name__)""",
            )
        for (s in samples) {
            assertTrue(regexes.any { it.containsMatchIn(s) }, "Expected at least one default pattern to match `$s`")
        }
    }

    @Test
    fun `default patterns match logging imports`() {
        val regexes = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.map { Regex(it) }
        val samples =
            listOf(
                """import logging""",
                """from logging import getLogger""",
                """from foo.bar import Logger""",
                """from foo import my_logger""",
            )
        for (s in samples) {
            assertTrue(regexes.any { it.containsMatchIn(s) }, "Expected at least one default pattern to match `$s`")
        }
    }
}
