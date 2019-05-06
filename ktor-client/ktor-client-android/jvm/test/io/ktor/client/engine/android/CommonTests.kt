/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.tests.*
import org.junit.*

class AndroidCookiesTest : CookiesTest(Android)

class AndroidAttributesTest : AttributesTest(Android)

class AndroidPostTest : PostTest(Android)

@Ignore
class AndroidMultithreadedTest : MultithreadedTest(Android)

class AndroidRedirectTest : HttpRedirectTest(Android)

class AndroidBuildersTest : BuildersTest(Android)

class AndroidHttpClientTest : HttpClientTest(Android)
