package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.require
import kotlin.test.*

class PrimitiveArraysTest {
    private val pool = VerifyingObjectPool(ChunkBuffer.NoPool)
    private val view = pool.borrow()

    private val i8 = byteArrayOf(-15, 0, 1, -1, 127)
    private val i16 = shortArrayOf(-15, 0, 1, 0xff, 0xffff.toShort(), 0xceff.toShort())
    private val i32 = intArrayOf(-15, 0, 1, 0xff, 0xffff, 0xffffffff.toInt(), 0xceffffff.toInt())
    private val i64 = longArrayOf(-15, 0, 1, 0xff, 0xffff, 0xffffffff, 0xceffffff, -1L)
    private val f32 = floatArrayOf(1.0f, 0.5f, -1.0f)
    private val f64 = doubleArrayOf(1.0, 0.5, -1.0)

    private val iu8 = i8.toUByteArray()
    private val iu16 = i16.toUShortArray()
    private val iu32 = i32.toUIntArray()
    private val iu64 = i64.toULongArray()

    @AfterTest
    fun test() {
        view.release(pool)
        pool.assertEmpty()
    }

    @Test
    fun testWriteByteArray() {
        val array = i8
        val size = 1

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f10001ff7f", view.readHex())
    }

    @Test
    fun testWriteUByteArray() {
        val array = iu8
        val size = 1

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f10001ff7f", view.readHex())
    }

    @Test
    fun testWriteByteArrayRange() {
        val array = i8

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0001ff", view.readHex())
    }

    @Test
    fun testWriteUByteArrayRange() {
        val array = iu8

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0001ff", view.readHex())
    }

    @Test
    fun testReadByteArray() {
        val array = i8
        val size = 1

        view.writeHex("f10001ff7f")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ByteArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadUByteArray() {
        val array = iu8
        val size = 1

        view.writeHex("f10001ff7f")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UByteArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadByteArrayRange() {
        val array = i8
        val size = 1

        view.writeHex("f10001ff7f")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ByteArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadUByteArrayRange() {
        val array = iu8
        val size = 1

        view.writeHex("f10001ff7f")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UByteArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asByteArray())
    }

    @Test
    fun testWriteShortArrayBE() {
        val array = i16
        val size = 2

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("fff10000000100ffffffceff", view.readHex())
    }

    @Test
    fun testWriteUShortArrayBE() {
        val array = iu16
        val size = 2

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("fff10000000100ffffffceff", view.readHex())
    }

    @Test
    fun testWriteShortArrayBERange() {
        val array = i16

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0000000100ffffff", view.readHex())
    }

    @Test
    fun testWriteUShortArrayBERange() {
        val array = iu16

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0000000100ffffff", view.readHex())
    }

    @Test
    fun testReadShortArrayBE() {
        val array = i16
        val size = 2

        view.writeHex("fff10000000100ffffffceff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ShortArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadUShortArrayBE() {
        val array = iu16
        val size = 2

        view.writeHex("fff10000000100ffffffceff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UShortArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadShortArrayRangeBE() {
        val array = i16
        val size = 2

        view.writeHex("fff10000000100ffffffceff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ShortArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadUShortArrayRangeBE() {
        val array = iu16
        val size = 2

        view.writeHex("fff10000000100ffffffceff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UShortArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asShortArray())
    }

    @Test
    fun testWriteShortArrayLE() {
        val array = i16
        val size = 2

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f1ff00000100ff00ffffffce", view.readHex())
    }

    @Test
    fun testWriteUShortArrayLE() {
        val array = iu16
        val size = 2

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f1ff00000100ff00ffffffce", view.readHex())
    }

    @Test
    fun testWriteShortArrayLERange() {
        val array = i16

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("00000100ff00ffff", view.readHex())
    }

    @Test
    fun testWriteUShortArrayLERange() {
        val array = iu16

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("00000100ff00ffff", view.readHex())
    }

    @Test
    fun testReadShortArrayLE() {
        val array = i16
        val size = 2

        view.writeHex("f1ff00000100ff00ffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ShortArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadUShortArrayLE() {
        val array = iu16
        val size = 2

        view.writeHex("f1ff00000100ff00ffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UShortArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadShortArrayRangeLE() {
        val array = i16
        val size = 2

        view.writeHex("f1ff00000100ff00ffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ShortArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadUShortArrayRangeLE() {
        val array = iu16
        val size = 2

        view.writeHex("f1ff00000100ff00ffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UShortArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asShortArray())
    }

    @Test
    fun testWriteIntArrayBE() {
        val array = i32
        val size = 4

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("fffffff10000000000000001000000ff0000ffffffffffffceffffff", view.readHex())
    }

    @Test
    fun testWriteUIntArrayBE() {
        val array = iu32
        val size = 4

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("fffffff10000000000000001000000ff0000ffffffffffffceffffff", view.readHex())
    }

    @Test
    fun testWriteIntArrayBERange() {
        val array = i32

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0000000000000001000000ff0000ffffffffffff", view.readHex())
    }

    @Test
    fun testWriteUIntArrayBERange() {
        val array = iu32

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0000000000000001000000ff0000ffffffffffff", view.readHex())
    }

    @Test
    fun testReadIntArrayBE() {
        val array = i32
        val size = 4

        view.writeHex("fffffff10000000000000001000000ff0000ffffffffffffceffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = IntArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadUIntArrayBE() {
        val array = iu32
        val size = 4

        view.writeHex("fffffff10000000000000001000000ff0000ffffffffffffceffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UIntArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadIntArrayRangeBE() {
        val array = i32
        val size = 4

        view.writeHex("fffffff10000000000000001000000ff0000ffffffffffffceffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = IntArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadUIntArrayRangeBE() {
        val array = iu32
        val size = 4

        view.writeHex("fffffff10000000000000001000000ff0000ffffffffffffceffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UIntArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asIntArray())
    }

    @Test
    fun testWriteIntArrayLE() {

        val array = i32
        val size = 4

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce", view.readHex())
    }

    @Test
    fun testWriteUIntArrayLE() {

        val array = iu32
        val size = 4

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce", view.readHex())
    }

    @Test
    fun testWriteIntArrayLERange() {

        val array = i32

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("0000000001000000ff000000ffff0000ffffffff", view.readHex())
    }

    @Test
    fun testWriteUIntArrayLERange() {

        val array = iu32

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("0000000001000000ff000000ffff0000ffffffff", view.readHex())
    }

    @Test
    fun testReadIntArrayLE() {

        val array = i32
        val size = 4

        view.writeHex("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = IntArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadUIntArrayLE() {

        val array = iu32
        val size = 4

        view.writeHex("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UIntArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadIntArrayRangeLE() {

        val array = i32
        val size = 4

        view.writeHex("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = IntArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadUIntArrayRangeLE() {
        val array = iu32
        val size = 4

        view.writeHex("f1ffffff0000000001000000ff000000ffff0000ffffffffffffffce")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = UIntArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asIntArray())
    }

    @Test
    fun testWriteLongArrayBE() {

        val array = i64
        val size = 8

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff", view.readHex())
    }

    @Test
    fun testWriteULongArrayBE() {

        val array = iu64
        val size = 8

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals(
            "fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff",
            view.readHex()
        )
    }

    @Test
    fun testWriteLongArrayBERange() {

        val array = i64

        view.writeFully(array, 1, array.size - 2)

        assertEquals("0000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffff", view.readHex())
    }

    @Test
    fun testWriteULongArrayBERange() {

        val array = iu64

        view.writeFully(array, 1, array.size - 2)

        assertEquals(
            "0000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffff",
            view.readHex()
        )
    }

    @Test
    fun testLongIntArrayBE() {

        val array = i64
        val size = 8

        view.writeHex("fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = LongArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testLongUIntArrayBE() {

        val array = iu64
        val size = 8

        view.writeHex("fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ULongArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadLongArrayRangeBE() {

        val array = i64
        val size = 8

        view.writeHex("fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = LongArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadULongArrayRangeBE() {

        val array = iu64
        val size = 8

        view.writeHex("fffffffffffffff10000000000000000000000000000000100000000000000ff000000000000ffff00000000ffffffff00000000ceffffffffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ULongArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asLongArray())
    }

    @Test
    fun testWriteLongArrayLE() {

        val array = i64
        val size = 8

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff", view.readHex())
    }

    @Test
    fun testWriteULongArrayLE() {

        val array = iu64
        val size = 8

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals(
            "f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff",
            view.readHex()
        )
    }

    @Test
    fun testWriteLongArrayLERange() {

        val array = i64

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000", view.readHex())
    }

    @Test
    fun testWriteULongArrayLERange() {

        val array = iu64

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals(
            "00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000",
            view.readHex()
        )
    }

    @Test
    fun testReadLongArrayLE() {

        val array = i64
        val size = 8

        view.writeHex("f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = LongArray(array.size)
        view.readFullyLittleEndian(tmp)
        if (!tmp.contentEquals(array)) {
            assertEquals(array.joinToString(), tmp.joinToString())
        }
    }

    @Test
    fun testReadULongArrayLE() {

        val array = iu64
        val size = 8

        view.writeHex("f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ULongArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadLongArrayRangeLE() {

        val array = i64
        val size = 8

        view.writeHex("f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = LongArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testReadULongArrayRangeLE() {

        val array = iu64
        val size = 8

        view.writeHex("f1ffffffffffffff00000000000000000100000000000000ff00000000000000ffff000000000000ffffffff00000000ffffffce00000000ffffffffffffffff")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = ULongArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp.asLongArray())
    }

    @Test
    fun testWriteFloatArrayBE() {

        val array = f32
        val size = 4

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("3f8000003f000000bf800000", view.readHex())
    }

    @Test
    fun testWriteFloatArrayBERange() {

        val array = f32

        view.writeFully(array, 1, array.size - 2)

        assertEquals("3f000000", view.readHex())
    }

    @Test
    fun testReadFloatArrayBE() {

        val array = f32
        val size = 4

        view.writeHex("3f8000003f000000bf800000")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = FloatArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadFloatArrayRangeBE() {

        val array = f32
        val size = 4

        view.writeHex("3f8000003f000000bf800000")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = FloatArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testWriteFloatArrayLE() {

        val array = f32
        val size = 4

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("0000803f0000003f000080bf", view.readHex())
    }

    @Test
    fun testWriteFloatArrayLERange() {

        val array = f32

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("0000003f", view.readHex())
    }

    @Test
    fun testReadFloatArrayLE() {

        val array = f32
        val size = 4

        view.writeHex("0000803f0000003f000080bf")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = FloatArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadFloatArrayRangeLE() {

        val array = f32
        val size = 4

        view.writeHex("0000803f0000003f000080bf")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = FloatArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testWriteDoubleArrayBE() {

        val array = f64
        val size = 8

        view.writeFully(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("3ff00000000000003fe0000000000000bff0000000000000", view.readHex())
    }

    @Test
    fun testWriteDoubleArrayBERange() {

        val array = f64

        view.writeFully(array, 1, array.size - 2)

        assertEquals("3fe0000000000000", view.readHex())
    }

    @Test
    fun testReadDoubleArrayBE() {

        val array = f64
        val size = 8

        view.writeHex("3ff00000000000003fe0000000000000bff0000000000000")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = DoubleArray(array.size)
        view.readFully(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadDoubleArrayRangeBE() {

        val array = f64
        val size = 8

        view.writeHex("3ff00000000000003fe0000000000000bff0000000000000")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = DoubleArray(array.size + 2)
        fill(tmp)
        view.readFully(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    @Test
    fun testWriteDoubleArrayLE() {

        val array = f64
        val size = 8

        view.writeFullyLittleEndian(array)

        assertEquals(array.size * size, view.readRemaining)
        assertEquals("000000000000f03f000000000000e03f000000000000f0bf", view.readHex())
    }

    @Test
    fun testWriteDoubleArrayLERange() {

        val array = f64

        view.writeFullyLittleEndian(array, 1, array.size - 2)

        assertEquals("000000000000e03f", view.readHex())
    }

    @Test
    fun testReadDoubleArrayLE() {

        val array = f64
        val size = 8

        view.writeHex("000000000000f03f000000000000e03f000000000000f0bf")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = DoubleArray(array.size)
        view.readFullyLittleEndian(tmp)
        assertTrue { tmp.contentEquals(array) }
    }

    @Test
    fun testReadDoubleArrayRangeLE() {

        val array = f64
        val size = 8

        view.writeHex("000000000000f03f000000000000e03f000000000000f0bf")

        assertEquals(array.size * size, view.readRemaining)
        val tmp = DoubleArray(array.size + 2)
        fill(tmp)
        view.readFullyLittleEndian(tmp, 1, tmp.size - 2)
        compareSubRange(tmp)
    }

    private fun Buffer.readHex() = buildString(readRemaining * 2) {
        repeat(readRemaining) {
            val i = readByte().toInt() and 0xff
            val l = i shr 4
            val r = i and 0x0f

            appendDigit(l)
            appendDigit(r)
        }
    }

    private fun StringBuilder.appendDigit(d: Int) {
        require(d < 16) { "digit $d should be in [0..15]" }
        require(d >= 0) { "digit $d should be in [0..15]" }

        if (d < 10) append('0' + d)
        else append('a' + (d - 10))
    }

    private fun Buffer.writeHex(hex: CharSequence) {
        for (idx in 0..hex.length - 2 step 2) {
            val l = unhex(hex[idx])
            val r = unhex(hex[idx + 1])

            writeByte((l shl 4 or r).toByte())
        }
    }

    private fun fill(array: ByteArray) {
        for (i in array.indices) {
            array[i] = 0xee.toByte()
        }
    }

    private fun fill(array: UByteArray) {
        for (i in array.indices) {
            array[i] = 0xeeu
        }
    }

    private fun fill(array: ShortArray) {
        for (i in array.indices) {
            array[i] = 0xeeee.toShort()
        }
    }

    private fun fill(array: UShortArray) {
        for (i in array.indices) {
            array[i] = 0xeeeeu
        }
    }

    private fun fill(array: IntArray) {
        for (i in array.indices) {
            array[i] = 0xeeeeeeee.toInt()
        }
    }

    private fun fill(array: UIntArray) {
        for (i in array.indices) {
            array[i] = 0xeeeeeeeeu
        }
    }

    private fun fill(array: LongArray) {
        for (i in array.indices) {
            array[i] = 0x0eeeeeeeeeeeeeeeL
        }
    }

    private fun fill(array: ULongArray) {
        for (i in array.indices) {
            array[i] = 0x0eeeeeeeeeeeeeeeuL
        }
    }

    private fun fill(array: FloatArray) {
        for (i in array.indices) {
            array[i] = Float.fromBits(0xeeeeeeee.toInt())
        }
    }

    private fun fill(array: DoubleArray) {
        for (i in array.indices) {
            array[i] = Double.fromBits(0x0eeeeeeeeeeeeeeeL)
        }
    }

    private fun compareSubRange(readBuffer: ByteArray) {
        assertEquals(0xee, readBuffer[0].toInt() and 0xff)
        assertEquals(0xee, readBuffer[readBuffer.lastIndex].toInt() and 0xff)

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(i8) }
    }

    private fun compareSubRange(readBuffer: ShortArray) {
        assertEquals(0xeeee, readBuffer[0].toInt() and 0xffff)
        assertEquals(0xeeee, readBuffer[readBuffer.lastIndex].toInt() and 0xffff)

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(i16) }
    }

    private fun compareSubRange(readBuffer: IntArray) {
        assertEquals(0xeeeeeeee, readBuffer[0].toLong() and 0xffffffff)
        assertEquals(0xeeeeeeee, readBuffer[readBuffer.lastIndex].toLong() and 0xffffffff)

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(i32) }
    }

    private fun compareSubRange(readBuffer: LongArray) {
        assertEquals(0x0eeeeeeeeeeeeeeeL, readBuffer[0])
        assertEquals(0x0eeeeeeeeeeeeeeeL, readBuffer[readBuffer.lastIndex])

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(i64) }
    }

    private fun compareSubRange(readBuffer: FloatArray) {
        assertEquals(0xeeeeeeee, readBuffer[0].toRawBits().toLong() and 0xffffffff)
        assertEquals(0xeeeeeeee, readBuffer[readBuffer.lastIndex].toRawBits().toLong() and 0xffffffff)

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(f32) }
    }

    private fun compareSubRange(readBuffer: DoubleArray) {
        assertEquals(0x0eeeeeeeeeeeeeeeL, readBuffer[0].toRawBits())
        assertEquals(0x0eeeeeeeeeeeeeeeL, readBuffer[readBuffer.lastIndex].toRawBits())

        assertTrue { readBuffer.copyOfRange(1, readBuffer.size - 1).contentEquals(f64) }
    }

    private fun unhex(h: Char): Int = if (h in '0'..'9') h - '0' else if (h in 'a'..'f') h - 'a' + 10 else fail()
}
