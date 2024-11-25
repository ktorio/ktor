/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.*

class EmbeddedServerTest {

    @Test
    fun `checkUrlMatches returns true when pattern matches part of URL path`() {
        val url = URI.create("file:///some/path/to/file.jar").toURL()
        val pattern = "path/to"

        assertTrue(checkUrlMatches(url, pattern))
    }

    @Test
    fun `checkUrlMatches returns false when pattern does not match URL path`() {
        val url = URI.create("file:///some/path/to/file.jar").toURL()
        val pattern = "another/path"

        assertFalse(checkUrlMatches(url, pattern))
    }

    @Test
    fun `checkUrlMatches returns true when pattern matches URL path case insensitively`() {
        val url = URI.create("file:///some/path/to/file.jar").toURL()
        val pattern = "PATH/TO"

        assertTrue(checkUrlMatches(url, pattern))
    }

    @Test
    fun `checkUrlMatches returns false when URL path is null`() {
        val url = URI.create("jar:file:///some/path/to/file.jar!/").toURL()
        val pattern = "path/to"

        // Simulating a null path by clearing the field via reflection (unsafe but for testing purposes)
        val urlField = url.javaClass.getDeclaredField("path")
        urlField.isAccessible = true
        urlField.set(url, null)

        assertFalse(checkUrlMatches(url, pattern))
    }

    @Test
    fun `checkUrlMatches returns true when pattern matches root URL path`() {
        val url = URI.create("file:///file.jar").toURL()
        val pattern = "file.jar"

        assertTrue(checkUrlMatches(url, pattern))
    }
}
