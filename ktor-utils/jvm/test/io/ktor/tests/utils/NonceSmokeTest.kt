package io.ktor.tests.utils

import io.ktor.util.*
import kotlin.test.*

class NonceSmokeTest {
    @Test
    fun test() {
        val nonceSet = HashSet<String>(4096)

        repeat(4096) {
            nonceSet.add(generateNonce())
        }

        assertTrue { nonceSet.size == 4096 }
    }
}
