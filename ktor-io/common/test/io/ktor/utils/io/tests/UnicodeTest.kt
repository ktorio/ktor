package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import kotlin.test.*

class UnicodeTest {
    @Test
    fun smokeTest() {
        val packet = buildPacket { append(smokeTestData) }.readBytes()
        assertEquals(smokeTestDataAsBytes.hexdump(), packet.hexdump())
    }

    @Test
    fun testInputEncoding() {
        val packet = buildPacket { append(testData) }.readBytes()

        assertEquals(testDataAsBytes.hexdump(), packet.hexdump())
    }

    @Test
    fun testInputCharArrayEncoding() {
        val packet = buildPacket { append(testDataCharArray) }.readBytes()

        assertEquals(testDataAsBytes.hexdump(), packet.hexdump())
    }

    @Test
    fun testInputDecoding() {
        val text = buildPacket { writeFully(testDataAsBytes) }.readText()

        assertEquals(testData, text)
    }

    companion object {
        private fun ByteArray.hexdump() = joinToString(separator = " ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        private const val smokeTestData = "\ud83c\udf00"

        private val smokeTestDataAsBytes = "f0 9f 8c 80".readHex()

        private const val testData = "file content with unicode " +
                "\ud83c\udf00 :" +
                " \u0437\u0434\u043e\u0440\u043e\u0432\u0430\u0442\u044c\u0441\u044f :" +
                " \uc5ec\ubcf4\uc138\uc694 :" +
                " \u4f60\u597d :" +
                " \u00f1\u00e7"

        private val testDataCharArray: CharArray = testData.toList().toCharArray()

        private val testDataAsBytes: ByteArray = ("66 69 6c 65 20 63 6f 6e 74 65 6e 74 20 77 69 74 " +
                " 68 20 75 6e 69 63 6f 64 65 20 f0 9f 8c 80 20 3a 20 d0 b7 d0 b4 d0 be d1 " +
                "80 d0 be d0 b2 d0 b0 d1 82 d1 8c d1 81 d1 8f 20 3a 20 ec 97 ac eb b3 b4 ec " +
            " 84 b8 ec 9a 94 20 3a 20 e4 bd a0 e5 a5 bd 20 3a 20 c3 b1 c3 a7").readHex()

        private fun String.readHex(): ByteArray = split(" ")
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
