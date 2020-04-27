/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import okhttp3.*
import kotlin.test.*

class RequestTests {

    class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            return response
        }
    }

    @Test
    fun testFeatures() = testWithEngine(OkHttp) {
        config {
            engine {
                addInterceptor(LoggingInterceptor())
                addNetworkInterceptor(LoggingInterceptor())
            }
        }

        test { client ->
            client.get<String>("https://google.com")
        }
    }
}
