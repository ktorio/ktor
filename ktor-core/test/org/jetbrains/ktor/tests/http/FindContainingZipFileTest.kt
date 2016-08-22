package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.content.*
import org.junit.*
import java.net.*
import kotlin.test.*

class FindContainingZipFileTest {
    @Test
    fun testSimpleJar() {
        assertEquals("/dist/app.jar", findContainingZipFile(URI("jar:file:/dist/app.jar/")).path.replace('\\', '/'))
    }

    @Test
    fun testNestedJar() {
        assertEquals("/dist/app.jar", findContainingZipFile(URI("jar:jar:file:/dist/app.jar!/my/jar.jar!/")).path.replace('\\', '/'))
    }

    @Test
    fun testEscapedChars() {
        assertEquals("/Program Files/app.jar", findContainingZipFile(URI("jar:file:/Program%20Files/app.jar/")).path.replace('\\', '/'))
    }
}