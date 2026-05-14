package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeFocusSettingsStateTest {
    @Test
    fun `default logging patterns are a single rule matching the literal substring 'logger dot'`() {
        val patterns = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        assertEquals(1, patterns.size, "Expected a single simple rule")
        assertEquals("""logger\.""", patterns.single(), "Expected the rule to be `logger\\.`")
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
    fun `default pattern intentionally ignores lines without the literal 'logger dot' substring`() {
        val regexes = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS.map { Regex(it) }
        // Per the simplified rule, only lines containing the literal `logger.` (lowercase
        // logger followed by a dot) are folded. That intentionally targets logger method
        // calls (`logger.warning(...)`, `self.logger.info(...)`, `_logger.debug(...)`),
        // and intentionally leaves alone:
        //   - assignments like `logger = logging.getLogger(...)` (logger followed by space)
        //   - imports like `from foo import my_logger` (no dot after logger)
        //   - `import logging` (no logger token at all)
        //   - capital-L `Logger` class references
        assertFalse(regexes.any { it.containsMatchIn("import logging") })
        assertFalse(regexes.any { it.containsMatchIn("from logging import LogLevel") })
        assertFalse(regexes.any { it.containsMatchIn("from logging import getLogger") })
        assertFalse(regexes.any { it.containsMatchIn("from foo import my_logger") })
        assertFalse(regexes.any { it.containsMatchIn("from foo.bar import Logger") })
        assertFalse(regexes.any { it.containsMatchIn("logger = logging.getLogger(__name__)") })
        assertFalse(regexes.any { it.containsMatchIn("""self.logger = logging.getLogger("X")""") })
    }
}
