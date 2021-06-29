/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.content.*
import kotlin.test.*

class FindContainingJarFileTest {
    @Test
    fun testSimpleJar() {
        assertEquals("/dist/app.jar", findContainingJarFile("jar:file:/dist/app.jar!/test").path.replace('\\', '/'))
    }

    @Test
    fun testSimpleJarNoFile() {
        assertEquals("/dist/app.jar", findContainingJarFile("jar:file:/dist/app.jar!").path.replace('\\', '/'))
    }

    @Test
    fun testEscapedChars() {
        assertEquals(
            "/Program Files/app.jar",
            findContainingJarFile("jar:file:/Program%20Files/app.jar!/test")
                .path.replace('\\', '/')
        )
    }
}
