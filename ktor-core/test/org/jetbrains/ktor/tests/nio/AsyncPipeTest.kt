package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.nio.*
import org.junit.*
import java.nio.*
import kotlin.concurrent.*
import kotlin.test.*

class AsyncPipeTest {
    val pipe = AsyncPipe()

    @Test
    fun testWriteEmpty() {
        var gotSuccess = false

        pipe.write(ByteBuffer.allocate(0), object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(0, count)
                gotSuccess = true
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertTrue(gotSuccess)
    }

    @Test
    fun testWriteOnly() {
        pipe.write(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })
    }

    @Test
    fun testReadOnlyEmpty() {
        var gotSuccess = false

        pipe.read(ByteBuffer.allocate(0), object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(0, count)
                gotSuccess = true
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertTrue(gotSuccess)
    }

    @Test
    fun testReadOnly() {
        pipe.read(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })
    }

    @Test
    fun testReadThenWrite() {
        val src = ByteBuffer.wrap("test".toByteArray(Charsets.ISO_8859_1))
        val dst = ByteBuffer.allocate(4)

        pipe.read(dst, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(4, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        pipe.write(src, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(4, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })


        dst.flip()
        assertEquals("test", dst.array().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun testWriteThenRead() {
        val src = ByteBuffer.wrap("test".toByteArray(Charsets.ISO_8859_1))
        val dst = ByteBuffer.allocate(4)

        pipe.write(src, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(4, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        pipe.read(dst, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(4, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })


        dst.flip()
        assertEquals("test", dst.array().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun testPartialRead() {
        val src = ByteBuffer.wrap("test".toByteArray(Charsets.ISO_8859_1))
        val dst = ByteBuffer.allocate(4)
        var writeSuccessCount = 0

        pipe.write(src, object: AsyncHandler {
            override fun success(count: Int) {
                writeSuccessCount++
                assertEquals(4, count)

                if (writeSuccessCount > 1) {
                    fail("Shouldn't be called more than once")
                }
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        dst.limit(2)
        pipe.read(dst, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(2, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertEquals(0, writeSuccessCount, "write success shouldn't be called yet")
        dst.flip()
        dst.compact()

        pipe.read(dst, object: AsyncHandler {
            override fun success(count: Int) {
                assertEquals(2, count)
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertEquals(1, writeSuccessCount)

        dst.flip()
        assertEquals("test", dst.array().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun testCloseAfterRead() {
        var gotSuccessEnd = false

        pipe.read(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                gotSuccessEnd = true
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertFalse(gotSuccessEnd)

        pipe.close()

        assertTrue(gotSuccessEnd)
    }

    @Test
    fun testCloseBeforeRead() {
        pipe.close()

        var gotSuccessEnd = false
        pipe.read(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                gotSuccessEnd = true
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertTrue(gotSuccessEnd)
    }

    @Test
    fun testCloseAfterWrite() {
        var gotFailed = false

        pipe.write(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                gotFailed = true
            }
        })

        assertFalse(gotFailed)

        pipe.close()

        assertTrue(gotFailed)
    }

    @Test
    fun testCloseBeforeWrite() {
        pipe.close()

        var gotFailed = false
        pipe.write(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                fail("unexpected")
            }

            override fun failed(cause: Throwable) {
                gotFailed = true
            }
        })

        assertTrue(gotFailed)
    }

    @Test
    fun testMultipleClose() {
        var gotSuccessEnd = 0

        pipe.read(ByteBuffer.allocate(1), object: AsyncHandler {
            override fun success(count: Int) {
                fail("unexpected")
            }

            override fun successEnd() {
                gotSuccessEnd++
            }

            override fun failed(cause: Throwable) {
                throw cause
            }
        })

        assertEquals(0, gotSuccessEnd)

        pipe.close()
        assertEquals(1, gotSuccessEnd)

        pipe.close()
        assertEquals(1, gotSuccessEnd)
    }

    @Test
    fun testPipeSync() {
        val reader = pipe.asInputStream().reader()
        val writer = pipe.asOutputStream().writer()

        thread {
            writer.use {
                it.write("test1")
                it.write("test2")
                it.write("test3")
            }
        }

        assertEquals("test1test2test3", reader.readText())
    }
}