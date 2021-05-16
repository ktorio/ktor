/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultHeadersTest {
    private val app = Application(
        object : ApplicationEnvironment {
            override val parentCoroutineContext get() = EmptyCoroutineContext
            override val log get() = TODO()
            override val config get() = TODO()
            override val monitor get() = TODO()
            override val rootPath get() = TODO()
            override val developmentMode get() = true
        }
    )

    private val call = TestApplicationCall(app, coroutineContext = EmptyCoroutineContext)

    @Test //TODO
    fun addsServerHeaderWithFallbackPackageNameAndVersion() = testSuspend {
        DefaultHeaders.Feature.install(app) {}
        app.execute(call, Unit)
        assertEquals("Ktor/debug", call.response.headers["Server"])
    }

    @Test
    fun serverHeaderIsNotModifiedIfPresent() = testSuspend {
        DefaultHeaders.Feature.install(app) {
            header(HttpHeaders.Server, "xserver/1.0")
        }
        app.execute(call, Unit)
        assertEquals("xserver/1.0", call.response.headers["Server"])
    }

}
