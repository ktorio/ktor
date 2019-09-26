/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class NeedRetryTest {

    var mustRespondBadRequest = true

    @Test
    fun testNeedRetryCalled() = clientTest(MockEngine {
        if (mustRespondBadRequest) {
            mustRespondBadRequest = false
            respondBadRequest()
        } else {
            respondOk("Hello")
        }
    }) {

        var retryBlockCallsCount = 0

        config {
            RetryCondition {
                retryCondition { _, response ->
                    retryBlockCallsCount += 1
                    response.status != HttpStatusCode.OK
                }
            }
        }

        test { client ->
            client.get<HttpResponse>().use {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals(2, retryBlockCallsCount)
            }
        }
    }
}
