/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.application.*
import io.ktor.server.testing.*
import io.ktor.utils.io.concurrent.*
import kotlin.test.*

class ApplicationEventTest {
    var c by shared(0)

    @Test
    fun testApplicationStopPreparing() {
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
    fun testApplicationStopping() {
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

    //TODO remove
    @Test
    fun testApplicationStopPreparingWithApplication() {
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
