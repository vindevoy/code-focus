package com.asynchrone.codefocus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeFocusBundleTest {

    @Test
    fun `bundle returns the configured plugin name`() {
        assertEquals("Code Focus", CodeFocusBundle.message("name"))
    }
}
