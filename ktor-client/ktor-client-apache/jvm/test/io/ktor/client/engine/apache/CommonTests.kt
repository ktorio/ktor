/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.tests.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.*


class ApacheCookiesTest : CookiesTest(Apache)

class ApacheAttributesTest : AttributesTest(Apache)

class ApachePostTest : PostTest(Apache.config {
    socketTimeout = 100_000
})

class ApacheMultithreadedTest : MultithreadedTest(Apache)

class ApacheFullFormTest : FullFormTest(Apache)

class ApacheRedirectTest : HttpRedirectTest(Apache)

class ApacheBuildersTest : BuildersTest(Apache)

class ApacheFeaturesTest : FeaturesTest(Apache)

class ApacheConnectionTest : ConnectionTest(Apache)

class ApacheHttpClientTest : HttpClientTest(Apache)

suspend fun main() {
    val client = HttpClient(Apache) {

        engine {
            socketTimeout = 0

            customizeClient {
                setMaxConnPerRoute(0)
                setMaxConnTotal(0)
            }
        }
    }

    var attempt = 0

    while(true) {
        val response = client.call("https://tickets.fcbayern.com/Internetverkauf/EventList.aspx") {
            method = HttpMethod.Get
        }.response

        println("[${++attempt}] Status Code: [${response.status.value}]")

        response.close()

        delay(5000)
    }
}
