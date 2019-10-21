/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.tests.*
import org.junit.*

class OkHttpCookiesTest : CookiesTest(OkHttp)

@Ignore
class OkHttpMultithreadedTest : MultithreadedTest(OkHttp)

class OkHttpBuildersTest : BuildersTest(OkHttp)

class OkHttpHttpClientTest : HttpClientTest(OkHttp)
