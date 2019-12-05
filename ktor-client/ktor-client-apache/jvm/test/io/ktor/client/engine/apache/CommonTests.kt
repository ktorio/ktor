/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.tests.*

class ApacheCookiesTest : CookiesTest(Apache)

class ApacheMultithreadedTest : MultithreadedTest(Apache)

class ApacheBuildersTest : BuildersTest(Apache)

class ApacheHttpClientTest : HttpClientTest(Apache)
