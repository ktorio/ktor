/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationEventTest {
    @Test
    fun `Checking the number of calls to ApplicationStopPreparing (use withTestApplication)`() {
        var c = 0

        withTestApplication {
            environment.monitor.subscribe(ApplicationStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            environment.monitor.subscribe(ApplicationStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun `Checking the number of calls to ApplicationStopping (use withTestApplication)`() {
        var c = 0

        withTestApplication {
            environment.monitor.subscribe(ApplicationStopping) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            environment.monitor.subscribe(ApplicationStopping) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun `Checking the number of calls to ApplicationStopPreparing (use withApplication)`() {
        var c = 0

        withApplication {
            environment.monitor.subscribe(ApplicationStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withApplication {
            environment.monitor.subscribe(ApplicationStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }
}
