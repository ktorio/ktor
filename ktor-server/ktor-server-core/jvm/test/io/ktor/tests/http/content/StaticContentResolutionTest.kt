/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.test.*

class StaticContentResolutionTest {

    private val baseUrl = StaticContentResolutionTest::class.java.classLoader.getResource("testjar.jar")

    @Test
    fun testResourceClasspathResourceWithDirectoryInsideJar() {
        val content = resourceClasspathResource(URL("jar:$baseUrl!/testdir"), "testdir") {
            ContentType.defaultForFileExtension(it)
        }

        assertNull(content)
    }

    @Test
    fun testResourceClasspathResourceWithFileInsideJar() {
        val content = resourceClasspathResource(URL("jar:$baseUrl!/testdir/testfile"), "testdir/testfile") {
            ContentType.defaultForFileExtension(it)
        }

        assertNotNull(content)
        assertTrue { content is OutgoingContent.ReadChannelContent }
        with(content as OutgoingContent.ReadChannelContent) {
            val data = String(runBlocking { readFrom().toByteArray() })
            assertEquals("test\n", data)
        }
    }
}
