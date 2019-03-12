package io.ktor.client.engine.android

import io.ktor.client.tests.*
import org.junit.*

class AndroidCookiesTest : CookiesTest(Android)

class AndroidAttributesTest : AttributesTest(Android)

class AndroidPostTest : PostTest(Android)

@Ignore
class AndroidMultithreadedTest : MultithreadedTest(Android)

class AndroidContentTest : ContentTest(Android)

class AndroidRedirectTest : HttpRedirectTest(Android)

class AndroidBuildersTest : BuildersTest(Android)

class AndroidHttpClientTest : HttpClientTest(Android)
