package io.ktor.tests.sessions

import io.ktor.sessions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import org.junit.*
import org.junit.Test
import java.io.*
import java.nio.file.*
import java.util.*
import kotlin.test.*

class DirectorySessionStorageTest {
    private val dir = Files.createTempDirectory("ktor-tests-").toFile()!!
    private val storage = directorySessionStorage(dir, false)

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
        storage.read("id0") { it.cancel() }
        Unit
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
            runBlocking { storage.read("id1") { it.cancel() } }
        }
        Unit
    }
}
