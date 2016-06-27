package org.jetbrains.ktor.tests.interception

import org.jetbrains.ktor.interception.*
import org.junit.*
import kotlin.test.*

class InterceptionTest {
    @Test fun `empty interception`() {
        assertEquals("value", Interceptable0 { "value" }.execute())
    }

    @Test fun `interception with augmented result`() {
        val interceptable0 = Interceptable0 { "value" }
        interceptable0.intercept { next -> next() + " next" }
        assertEquals("value next", interceptable0.execute())
    }
}
