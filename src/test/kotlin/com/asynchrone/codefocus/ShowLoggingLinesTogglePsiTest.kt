package com.asynchrone.codefocus

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement

/**
 * PSI-level verification that every logger-call in the test fixture is
 * actually reachable by `findChildrenOfAnyType` and that the default substring
 * set matches the PSI element's `.text` for each one. This is what the
 * `Show Logging Lines` toggle iterates over at apply time, so a failure
 * here directly explains why a line doesn't fold.
 */
@Suppress("ktlint:standard:function-naming")
class ShowLoggingLinesTogglePsiTest : BasePlatformTestCase() {
    private val testFile =
        """
        import logging

        logger = logging.getLogger(__name__)


        def fetch(url, retries=3):
            from logging import getLogger

            local_logger = getLogger("Test")
            local_logger.debug("inline import logger")

            attempt = 0
            while attempt < retries:
                attempt += 1
                try:
                    return {}
                except ValueError as exc:
                    last_error = exc

                    # Two consecutive standalone comments deep inside a function,
                    # to verify that grouped-comment folding works at depth.
                    logger.warning("retry %d for %s: %s", attempt, url, exc)


        def main():
            import sys
            logger.info("running with %s", sys.argv[0])
            return 0
        """.trimIndent()

    private val needles = CodeFocusSettingsState.DEFAULT_LOGGING_PATTERNS

    private fun matches(text: String): Boolean = needles.any { it.isNotEmpty() && text.contains(it) }

    fun `test psi finds the deeply nested logger warning statement`() {
        val psiFile = myFixture.configureByText("test.py", testFile)
        val exprStatements =
            PsiTreeUtil.findChildrenOfAnyType(
                psiFile,
                PyExpressionStatement::class.java,
            )
        val texts = exprStatements.map { it.text }
        val warning = texts.firstOrNull { it.startsWith("logger.warning") }
        assertNotNull("`logger.warning(...)` PyExpressionStatement must be reachable via findChildrenOfAnyType", warning)
    }

    fun `test default substrings match every logger and logging statement in the fixture`() {
        val psiFile = myFixture.configureByText("test.py", testFile)
        val allStatements =
            PsiTreeUtil.findChildrenOfAnyType(
                psiFile,
                PyImportStatement::class.java,
                PyFromImportStatement::class.java,
                PyAssignmentStatement::class.java,
                PyExpressionStatement::class.java,
            )

        // Plain substring matching: any of `logger`, `Logger`, `logging` anywhere in
        // the statement text triggers a fold. That covers imports, assignments, calls,
        // and factory references uniformly.
        val expectedMatches =
            listOf(
                "import logging",
                "from logging import getLogger",
                "logger = logging.getLogger(__name__)",
                "local_logger = getLogger(\"Test\")",
                "local_logger.debug(\"inline import logger\")",
                "logger.warning(\"retry %d for %s: %s\", attempt, url, exc)",
                "logger.info(\"running with %s\", sys.argv[0])",
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

    fun `test changing settings while toggle is off triggers live re-fold via listener`() {
        myFixture.configureByText("test.py", testFile)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false

        // Narrow the substrings to a single token that only the warning() call line contains.
        val state = CodeFocusSettingsState.getInstance(project)
        state.loggingPatterns = listOf("logger.warning")

        val foldedTextsAfter =
            myFixture.editor.foldingModel.allFoldRegions
                .filter { it.isValid && !it.isExpanded }
                .map {
                    myFixture.editor.document.getText(
                        com.intellij.openapi.util
                            .TextRange(it.startOffset, it.endOffset),
                    )
                }

        val matchingFolds = foldedTextsAfter.filter { it.contains("logger.warning") }
        assertTrue(
            "After narrowing substrings to `logger.warning`, expected the warning line folded. " +
                "Got: ${foldedTextsAfter.joinToString(" | ") { it.replace("\n", "\\n") }}",
            matchingFolds.isNotEmpty(),
        )
    }

    fun `test toggle off creates a fold region for every matched logger line`() {
        myFixture.configureByText("test.py", testFile)
        val toggle = ShowLoggingLinesToggle(myFixture.editor)
        toggle.isOn = false
        val regions = myFixture.editor.foldingModel.allFoldRegions
        val foldedTexts =
            regions
                .filter { it.isValid && !it.isExpanded }
                .map {
                    myFixture.editor.document.getText(
                        com.intellij.openapi.util
                            .TextRange(it.startOffset, it.endOffset),
                    )
                }
        val expectedFoldedSnippets =
            listOf(
                "import logging",
                "from logging import getLogger",
                "logger = logging.getLogger(__name__)",
                "local_logger = getLogger(\"Test\")",
                "local_logger.debug(\"inline import logger\")",
                "logger.warning(\"retry",
                "logger.info(\"running with",
            )
        for (snippet in expectedFoldedSnippets) {
            val match = foldedTexts.any { it.contains(snippet) }
            assertTrue(
                "Expected a collapsed fold region whose text contains `$snippet`. " +
                    "Folded regions found: ${foldedTexts.joinToString(" | ") { it.replace("\n", "\\n") }}",
                match,
            )
        }
    }
}
