package org.jetbrains.ktor.tests.session

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
    fun testMissingSession() {
        storage.read("id0") {}
    }

    @Test
    fun testSaveSimple() {
        storage.save("id1") { it.writer().use { it.write("test1") } }
        assertEquals("test1", storage.read("id1") { it.reader().readText() }.get())
    }

    @Test
    fun testInvalidate() {
        testSaveSimple()
        storage.invalidate("id1").get()
        assertFailsWith(NoSuchElementException::class) {
            storage.read("id1") {}
        }
    }
}
