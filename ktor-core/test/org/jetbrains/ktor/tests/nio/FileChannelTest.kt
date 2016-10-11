package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.nio.*
import org.junit.*
import java.io.*
import kotlin.test.*

class FileChannelTest {
    private val sandbox = File("target/files")
    private lateinit var temp: File

    @Before
    fun setUp() {
        if (!sandbox.mkdirs() && !sandbox.isDirectory) {
            fail()
        }

        temp = File.createTempFile("file", "", sandbox)
    }

    @Test
    fun testEmptyFileDefaults() {
        assertEquals(0, temp.asyncReadOnlyFileChannel().asInputStream().use { it.readBytes().size })
    }

    @Test
    fun testSingleByteFile() {
        temp.writeBytes(byteArrayOf(7))

        assertEquals(listOf(7.toByte()), temp.asyncReadOnlyFileChannel().asInputStream().use { it.readBytes().toList() })
    }

    @Test
    fun testSingleByteFileOffsetEnd() {
        temp.writeBytes(byteArrayOf(7))

        assertEquals(0, temp.asyncReadOnlyFileChannel(start = 1L).asInputStream().use { it.readBytes().size })
    }

    @Test
    fun testSingleByteDrop1Take1() {
        temp.writeBytes(byteArrayOf(7, 8, 9))

        assertEquals(listOf(8.toByte()), temp.asyncReadOnlyFileChannel(start = 1L, endInclusive = 1L).asInputStream().use { it.readBytes().toList() })
    }
}