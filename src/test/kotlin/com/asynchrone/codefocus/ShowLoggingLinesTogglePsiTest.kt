package com.asynchrone.codefocus

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import java.io.File

/**
 * Platform-level integration tests for [ShowLoggingLinesToggle]. Boots an in-process
 * IntelliJ Platform with a real `Editor` + PSI + `FoldingModel`, loads a focused
 * fixture, flips the toggle, and asserts on which fold regions are collapsed.
 *
 * Two fixtures are used:
 *  - `resources/python/test-logging-lines.py` — small, focused, easy to audit.
 *  - `resources/python/test.py` — the comprehensive real fixture that the entire
 *    plugin is exercised against day-to-day. Catches surprises that wouldn't show
 *    up in the small file.
 */
@Suppress("ktlint:standard:function-naming")
class ShowLoggingLinesTogglePsiTest : BasePlatformTestCase() {
    private val focusedFixture: String = File("resources/python/test-logging-lines.py").readText()
    private val realFixture: String = File("resources/python/test.py").readText()

    private val needles = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS

    private fun matches(text: String): Boolean = needles.any { it.isNotEmpty() && text.contains(it) }

    public override fun setUp() {
        super.setUp()
        // BasePlatformTestCase reuses the same project across test methods in the
        // class, so state mutations leak between tests. Reset patterns to defaults
        // before each test so every method starts from the standard ["logger",
        // "Logger", "logging"] needles.
        CodeFocusSettingsState.getInstance(project).loggingPatterns =
            CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS
    }

    private fun collapsedTexts(): List<String> =
        myFixture.editor.foldingModel.allFoldRegions
            .filter { it.isValid && !it.isExpanded }
            .map { myFixture.editor.document.getText(TextRange(it.startOffset, it.endOffset)) }

    fun `test psi finds the deeply nested logger warning statement in the focused fixture`() {
        val psiFile = myFixture.configureByText("test-logging-lines.py", focusedFixture)
        val exprStatements = PsiTreeUtil.findChildrenOfAnyType(psiFile, PyExpressionStatement::class.java)
        val warning = exprStatements.map { it.text }.firstOrNull { it.startsWith("logger.warning") }
        assertNotNull(
            "`logger.warning(...)` PyExpressionStatement must be reachable via findChildrenOfAnyType",
            warning,
        )
    }

    fun `test default substrings match every expected logger statement in the focused fixture`() {
        val psiFile = myFixture.configureByText("test-logging-lines.py", focusedFixture)
        val allStatements =
            PsiTreeUtil.findChildrenOfAnyType(
                psiFile,
                PyImportStatement::class.java,
                PyFromImportStatement::class.java,
                PyAssignmentStatement::class.java,
                PyExpressionStatement::class.java,
            )

        val expectedMatches =
            listOf(
                "import logging",
                "from logging import getLogger",
                "logger = logging.getLogger(__name__)",
                "local_logger = getLogger(\"Test\")",
                "local_logger.debug(\"hello\")",
                "logger.warning(\"retry %s: %s\", url, exc)",
                "self.logger = logging.getLogger(\"svc\")",
                "self.logger.info(\"running\")",
            )

        for (expected in expectedMatches) {
            val stmt = allStatements.firstOrNull { it.text == expected }
            assertNotNull("Expected to find statement: $expected", stmt)
            assertTrue(
                "Default substrings must match `$expected`",
                matches(stmt!!.text),
            )
        }
    }

    fun `test toggle off folds every logger line in the focused fixture`() {
        myFixture.configureByText("test-logging-lines.py", focusedFixture)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()
        val expectedSnippets =
            listOf(
                "import logging",
                "from logging import getLogger",
                "logger = logging.getLogger(__name__)",
                "local_logger.debug",
                "logger.warning(\"retry",
                "self.logger.info(\"running\")",
            )
        for (snippet in expectedSnippets) {
            assertTrue(
                "Expected a collapsed fold whose text contains `$snippet`. " +
                    "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
                collapsed.any { it.contains(snippet) },
            )
        }
    }

    fun `test changing settings while toggle is off triggers live re-fold via listener`() {
        myFixture.configureByText("test-logging-lines.py", focusedFixture)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false

        // Narrow the substrings to a single token only the warning() call line contains.
        val state = CodeFocusSettingsState.getInstance(project)
        state.loggingPatterns = listOf("logger.warning")

        val collapsed = collapsedTexts()
        val matchingFolds = collapsed.filter { it.contains("logger.warning") }
        assertTrue(
            "After narrowing substrings to `logger.warning`, expected the warning line folded. " +
                "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
            matchingFolds.isNotEmpty(),
        )
    }

    fun `test issue 52 - blank lines around a standalone logger line fold get absorbed`() {
        val fixture = "x = 1\n\nlogger.info(\"hi\")\n\ny = 2\n"
        myFixture.configureByText("issue52-snippet.py", fixture)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false

        val document = myFixture.editor.document
        val collapsed =
            myFixture.editor.foldingModel.allFoldRegions
                .filter { it.isValid && !it.isExpanded }

        assertEquals(
            "Expected exactly one collapsed logger fold for `$fixture`.",
            1,
            collapsed.size,
        )

        val fold = collapsed.single()
        val text = document.getText(TextRange(fold.startOffset, fold.endOffset))

        assertEquals(
            "Fold should swallow the blank line above and the blank line below the logger statement.",
            "\nlogger.info(\"hi\")\n\n",
            text,
        )
    }

    fun `test toggle off folds every logger line in the comprehensive real fixture`() {
        myFixture.configureByText("test.py", realFixture)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false

        val collapsed = collapsedTexts()
        val expectedSnippets =
            listOf(
                "import logging",
                "from logging import getLogger",
                "logger = logging.getLogger(__name__)",
                "local_logger = getLogger(\"Test\")",
                "local_logger.debug",
                "logger.warning(\"retry",
                "logger.info(\"running",
            )
        for (snippet in expectedSnippets) {
            assertTrue(
                "Expected a collapsed fold whose text contains `$snippet` (real fixture). " +
                    "Got: ${collapsed.joinToString(" | ") { it.replace("\n", "\\n").take(120) }}",
                collapsed.any { it.contains(snippet) },
            )
        }
    }
}
