package io.ktor.utils.io.tests

import io.ktor.utils.io.pool.*
import kotlin.test.*

class DefaultPoolImplementationTest {
    @Test
    fun instantiateTest() {
        assertEquals(1, Impl().borrow())
    }

    private class Impl : DefaultPool<Int>(10) {
        override fun produceInstance() = 1
    }
}
