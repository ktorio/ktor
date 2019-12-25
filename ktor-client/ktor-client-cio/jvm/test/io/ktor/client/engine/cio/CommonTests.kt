/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.tests.*

class CIOCookiesTest : CookiesTest(CIO)

class CIOMultithreadedTest : MultithreadedTest(CIO)

class CIOBuildersTest : BuildersTest(CIO)

class CIOHttpClientTest : HttpClientTest(CIO)
