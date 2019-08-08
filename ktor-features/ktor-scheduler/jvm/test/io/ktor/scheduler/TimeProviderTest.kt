/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

@InternalCoroutinesApi
class TimeProviderTest {

    @Test
    fun localDateTimeMinusTest() {
        assertEquals(1 * SEC_TO_NS + 100,
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1, 314) -
                LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 214)
        )
        assertEquals(1 * SEC_TO_NS - 200,
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1, 0) -
                LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 200)
        )
        assertEquals(1 * SEC_TO_NS - 100 * MS_TO_NS,
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1, 0) -
                LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 100 * MS_TO_NS)
        )
    }

}
