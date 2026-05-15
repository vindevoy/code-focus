package com.asynchrone.codefocus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Shared helper for the toggles that hide PSI ranges by adding fold regions
 * (Show Comments, Show Logging Lines, Show Imports). Computes the offset pair
 * the toggle should pass to `FoldingModel.addFoldRegion`.
 *
 * Behaviour:
 *  1. If the foldable range starts mid-line (any non-whitespace to its left on
 *     the same line), the range is returned unchanged so the fold sits inside
 *     the existing code line — e.g. a trailing `# inline comment` on a real
 *     statement.
 *  2. Otherwise, the start is moved to the line start and the end past the
 *     trailing newline of the range's last line.
 *  3. After (2), runs of blank-only lines immediately above and below the
 *     range are absorbed into the fold so the collapsed placeholder does not
 *     leave orphaned blank lines around it (issue #52).
 *
 * Leading blank-line absorption is clipped at [previousFoldEnd] so adjacent
 * fold regions, processed in document order, do not race for the same blank
 * line. Callers should sort their ranges by start offset and pass the previous
 * fold's end as `previousFoldEnd` on each iteration.
 */
object FoldExpansion {
    fun expand(
        document: Document,
        range: TextRange,
        previousFoldEnd: Int = 0,
    ): Pair<Int, Int> {
        val lineStart = document.getLineStartOffset(document.getLineNumber(range.startOffset))
        val prefix = document.getText(TextRange(lineStart, range.startOffset))

        if (prefix.any { !it.isWhitespace() }) {
            return range.startOffset to range.endOffset
        }

        var startLine = document.getLineNumber(lineStart)

        while (startLine > 0) {
            val candidate = startLine - 1
            val candidateStart = document.getLineStartOffset(candidate)

            if (candidateStart < previousFoldEnd) break
            if (!isBlankLine(document, candidate)) break

            startLine = candidate
        }

        val newStart = document.getLineStartOffset(startLine)
        val rangeEnd = range.endOffset
        val withNewline =
            if (rangeEnd < document.textLength && document.charsSequence[rangeEnd] == '\n') {
                rangeEnd + 1
            } else {
                rangeEnd
            }

        var endLine = document.getLineNumber((withNewline - 1).coerceAtLeast(newStart))

        while (endLine + 1 < document.lineCount) {
            val candidate = endLine + 1

            if (!isBlankLine(document, candidate)) break

            endLine = candidate
        }

        val newEnd =
            if (endLine + 1 < document.lineCount) {
                document.getLineStartOffset(endLine + 1)
            } else {
                document.textLength
            }

        return newStart to newEnd
    }

    private fun isBlankLine(
        document: Document,
        lineNumber: Int,
    ): Boolean {
        val start = document.getLineStartOffset(lineNumber)
        val end = document.getLineEndOffset(lineNumber)

        return document.getText(TextRange(start, end)).all { it.isWhitespace() }
    }
}
