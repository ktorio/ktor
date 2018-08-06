package io.ktor.client.engine.android

import io.ktor.client.tests.*
import org.junit.*


@Ignore
class AndroidCacheTest : CacheTest(Android)

class AndroidCookiesTest : CookiesTest(Android)

class AndroidPostTest : PostTest(Android)

class AndroidMultithreadedTest : MultithreadedTest(Android)

class AndroidFullFormTest : FullFormTest(Android)

class AndroidContentTest : ContentTest(Android)

class AndroidRedirectTest : HttpRedirectTest(Android)

class AndroidBuildersTest : BuildersTest(Android)
