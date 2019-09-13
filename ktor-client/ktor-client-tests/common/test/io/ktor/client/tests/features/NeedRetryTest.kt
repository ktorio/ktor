/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class NeedRetryTest {

    @Test
    fun testNeedRetryCalled() = clientTest(MockEngine {
        respondOk("Hello")
    }) {

        var retryConditionBlockCalled = false

        config {
            RetryCondition {
                retryCondition { requestBuilder, response ->
                    retryConditionBlockCalled = true
                    false
                }
            }
        }

        test { client ->
            client.get<HttpResponse>()
            assertTrue(retryConditionBlockCalled, "Need retry condition block never called")
        }
    }
}
