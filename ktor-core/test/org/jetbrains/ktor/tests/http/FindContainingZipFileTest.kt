package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.content.*
import org.junit.*
import java.net.*
import kotlin.test.*

class FindContainingZipFileTest {
    @Test
    fun testSimpleJar() {
        assertEquals("/dist/app.jar", findContainingZipFile("jar:file:/dist/app.jar!/test").path.replace('\\', '/'))
    }

    @Test
    fun testSimpleJarNoFile() {
        assertEquals("/dist/app.jar", findContainingZipFile("jar:file:/dist/app.jar!").path.replace('\\', '/'))
    }

    @Test
    fun testEscapedChars() {
        assertEquals("/Program Files/app.jar", findContainingZipFile("jar:file:/Program%20Files/app.jar!/test").path.replace('\\', '/'))
    }
}