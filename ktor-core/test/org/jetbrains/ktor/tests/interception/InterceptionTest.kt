package org.jetbrains.ktor.tests.interception

import org.jetbrains.ktor.interception.*
import org.junit.*
import kotlin.test.*

class InterceptionTest {
    @Test fun `empty interception`() {
        assertEquals("value", Interceptable0 { "value" }.execute())
        assertEquals("VALUE", Interceptable1<String, String> { it.toUpperCase() }.execute("value"))
        assertEquals("value1", Interceptable2<Int, String, String> { index, value -> value + index }.execute(1, "value"))
    }

    @Test fun `interception with augmented result`() {
        val interceptable0 = Interceptable0 { "value" }
        interceptable0.intercept { next -> next() + " next" }
        assertEquals("value next", interceptable0.execute())

        val interceptable1 = Interceptable1<String, String> { it.toUpperCase() }
        interceptable1.intercept { param, next -> next(param) + " next" }
        assertEquals("VALUE next", interceptable1.execute("value"))

        val interceptable2 = Interceptable2<Int, String, String> { index, value -> value + index }
        interceptable2.intercept { index, param, next -> next(index, param) + " next" }
        assertEquals("value1 next", interceptable2.execute(1, "value"))
    }

    @Test fun `interception with augmented param`() {
        val interceptable1 = Interceptable1<String, String> { it.toUpperCase() }
        interceptable1.intercept { param, next -> next(param + " next") }
        assertEquals("VALUE NEXT", interceptable1.execute("value"))

        val interceptable2 = Interceptable2<Int, String, String> { index, value -> value + index }
        interceptable2.intercept { index, param, next -> next(index + 1, param + " next") }
        assertEquals("value next2", interceptable2.execute(1, "value"))
    }
}
