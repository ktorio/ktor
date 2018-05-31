package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Assert.*
import org.junit.*
import java.util.*


class CodecJvmTest {
    @Test(timeout = 1000L)
    fun testDecodeRandom() {
        val rnd = Random()
        val chars = "+%0123abc"

        for (step in 0..1000) {
            val size = rnd.nextInt(15) + 1
            val sb = CharArray(size)

            for (i in 0 until size) {
                sb[i] = chars[rnd.nextInt(chars.length)]
            }

            try {
                decodeURLQueryComponent(String(sb))
            } catch (ignore: URLDecodeException) {
            } catch (t: Throwable) {
                fail("Failed at ${String(sb)}")
            }
        }
    }

}