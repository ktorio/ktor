/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class ActualFileSystemTest {
    private val fs = ActualFileSystem()

    @BeforeTest
    fun remove() {
        fs.listOrNull("build/tmp")?.forEach {
            val name = it.substringAfterLast("/")
            if (name.startsWith("f") && name.endsWith(".tmp")) {
                fs.delete(it)
            }
        }
    }

    @Test
    fun listSmokeTest() {
        val files = fs.list(".")
        assertNotEquals(listOf(), files)
    }

    @Test
    fun open() {
        fs.open("build/tmp/f1.tmp").use { out ->
            out.writeText("test")
        }
    }

    @Test
    fun openAndDelete() {
        fs.open("build/tmp/f2.tmp").use { out ->
            out.writeText("test")
        }
        assertEquals(4, fs.size("build/tmp/f2.tmp"))
        fs.delete("build/tmp/f2.tmp")
        assertEquals(0, fs.size("build/tmp/f2.tmp"))
    }

    @Test
    fun openAndRename() {
        fs.open("build/tmp/f3.tmp").use { out ->
            out.writeText("test")
        }
        assertTrue(fs.rename("build/tmp/f3.tmp", "build/tmp/f33.tmp"))
    }

    @Test
    fun openAndModificationTime() {
        fs.open("build/tmp/f4.tmp").use { out ->
            out.writeText("test")
        }

        val date = fs.lastModified("build/tmp/f4.tmp")
        val now = GMTDate()

        // note: 2000 - fs mtime precision
        // 30000 just random time, not that much and not that small
        assertTrue(date <= now + 2000, "Modification date should be in the past: $date, $now")
        assertTrue(date > now - 30000, "Modification date shouldn't be too old: $date, $now")
    }

    @Test
    fun openAndSize() {
        fs.open("build/tmp/f5.tmp").use { out ->
            out.writeText("test")
        }

        assertEquals(4, fs.size("build/tmp/f5.tmp"))
        assertEquals(0, fs.size("build/tmp/f5.tmp0"))
    }

    @Test
    fun list() {
        fs.open("build/tmp/f6.tmp").close()
        fs.open("build/tmp/f61.tmp").close()
        assertTrue("build/tmp/f6.tmp" in fs.list("build/tmp"))
        assertTrue("build/tmp/f61.tmp" in fs.list("build/tmp"))
    }

    private fun FileSystem.listOrNull(path: String): List<String>? = try {
        list(path)
    } catch (_: Throwable) {
        null
    }
}
