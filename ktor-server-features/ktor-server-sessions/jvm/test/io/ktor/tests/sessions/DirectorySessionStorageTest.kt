/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.*
import kotlin.test.*

class DirectorySessionStorageTest {
    private val dir = Files.createTempDirectory("ktor-tests-").toFile()!!
    private val storage = directorySessionStorage(dir, false)

    @AfterTest
    fun tearDown() {
        (storage as Closeable).close()
        assertTrue { dir.deleteRecursively() }
    }

    @Test
    fun testSetup() {
    }

    @Test(expected = NoSuchElementException::class)
    fun testMissingSession(): Unit = runBlocking {
        storage.read("id0") { it.cancel() }
        Unit
    }

    @Test
    fun testSaveSimple(): Unit = runBlocking {
        storage.write("id1") { it.toOutputStream().writer().use { it.write("test1") } }
        assertEquals("test1", storage.read("id1") { it.toInputStream().reader().use { it.readText() } })
    }

    @Test
    fun testSaveTwice(): Unit = runBlocking {
        storage.write("id1") { it.toOutputStream().writer().use { it.write("test1 with tail") } }
        storage.write("id1") { it.toOutputStream().writer().use { it.write("test2") } }
        assertEquals("test2", storage.read("id1") { it.toInputStream().reader().use { it.readText() } })
    }

    @Test
    fun testInvalidate(): Unit = runBlocking {
        testSaveSimple()
        storage.invalidate("id1")
        assertFailsWith(NoSuchElementException::class) {
            runBlocking { storage.read("id1") { it.cancel() } }
        }
        Unit
    }
}
