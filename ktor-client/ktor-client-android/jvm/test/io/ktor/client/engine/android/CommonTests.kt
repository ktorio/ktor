/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.tests.*

class AndroidCookiesTest : CookiesTest(Android)

class AndroidMultithreadedTest : MultithreadedTest(Android)

class AndroidBuildersTest : BuildersTest(Android)

class AndroidHttpClientTest : HttpClientTest(Android)
