package org.jetbrains.ktor.tests.session

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.sessions.*
import org.junit.*
import java.io.*
import java.nio.file.*
import java.util.*
import kotlin.test.*

class DirectorySessionStorageTest {
    val dir = Files.createTempDirectory("ktor-tests-").toFile()
    val storage = directorySessionStorage(dir, false)

    @After
    fun tearDown() {
        (storage as Closeable).close()
        assertTrue { dir.deleteRecursively() }
    }

    @Test
    fun testSetup() {
    }

    @Test(expected = NoSuchElementException::class)
    fun testMissingSession() = runBlocking {
        storage.read("id0") { it.close() }
    }

    @Test
    fun testSaveSimple() = runBlocking {
        storage.write("id1") { it.toOutputStream().writer().use { it.write("test1") } }
        assertEquals("test1", storage.read("id1") { it.toInputStream().reader().use { it.readText() } })
    }

    @Test
    fun testInvalidate() = runBlocking {
        testSaveSimple()
        storage.invalidate("id1")
        assertFailsWith(NoSuchElementException::class) {
            runBlocking { storage.read("id1") { it.close() } }
        }
        Unit
    }
}
