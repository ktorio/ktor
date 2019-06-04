/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import io.ktor.client.tests.*


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
