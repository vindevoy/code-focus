package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeFocusSettingsStateTest {
    @Test
    fun `default logging patterns are three plain substrings (logger, Logger, logging)`() {
        val patterns = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        assertEquals(listOf("logger", "Logger", "logging"), patterns)
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
        val custom = listOf("foo", "bar")
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
        state.loggingPatterns = listOf("a")
        state.loggingPatterns = listOf("b")
        state.loggingPatterns = listOf("a")
        assertEquals(3, firedCount, "Listener must fire on every setter call, even when the value is unchanged")
    }

    @Test
    fun `removed patterns listener does not fire`() {
        val state = CodeFocusSettingsState()
        var firedCount = 0
        val listener = CodeFocusSettingsState.PatternsListener { firedCount++ }
        state.addPatternsListener(listener)
        state.removePatternsListener(listener)
        state.loggingPatterns = listOf("a")
        assertEquals(0, firedCount)
    }

    @Test
    fun `multiple listeners all fire in registration order`() {
        val state = CodeFocusSettingsState()
        val order = mutableListOf<String>()
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "a" })
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "b" })
        state.addPatternsListener(CodeFocusSettingsState.PatternsListener { order += "c" })
        state.loggingPatterns = listOf("x")
        assertEquals(listOf("a", "b", "c"), order)
    }

    @Test
    fun `default substrings cover every common logger and logging shape`() {
        val needles = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        val matchedSamples =
            listOf(
                "import logging",
                "from logging import getLogger",
                "from logging import LogLevel",
                "from foo import my_logger",
                "from foo import logger",
                "from foo.bar import Logger",
                "logger = logging.getLogger(__name__)",
                """self.logger = logging.getLogger("X")""",
                "_logger = logging.getLogger(__name__)",
                "logger.warning(\"x\")",
                "    logger.warning(\"x\")",
                "self.logger.warning(\"x\")",
                "module.sub.logger.info(\"x\")",
                "_logger.debug(\"x\")",
                "my_logger.error(\"x\")",
                "LoggerFactory.getLogger(__name__)",
            )
        for (s in matchedSamples) {
            assertTrue(needles.any { it.isNotEmpty() && s.contains(it) }, "Expected default substrings to match `$s`")
        }
    }

    @Test
    fun `default substrings leave alone lines with no logging-related token`() {
        val needles = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
        val unrelatedSamples =
            listOf(
                "x = 42",
                "print(\"hello\")",
                "from collections import defaultdict",
                "def main(): pass",
                "raise RuntimeError(\"boom\")",
            )
        for (s in unrelatedSamples) {
            assertFalse(
                needles.any { it.isNotEmpty() && s.contains(it) },
                "Expected default substrings NOT to match `$s`",
            )
        }
    }
}
