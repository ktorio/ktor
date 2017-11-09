package io.ktor.client.backend.apache

import io.ktor.client.tests.*
import org.junit.experimental.runners.*
import org.junit.runner.*


@RunWith(Enclosed::class)
class ApacheClientTestSuite {
    class ApacheCacheTest : CacheTest(ApacheBackend)

    class ApacheCookiesTest : CookiesTest(ApacheBackend)

    class ApachePostTests : PostTest(ApacheBackend)

    class ApacheMultithreadedTest : MultithreadedTest(ApacheBackend)

    class ApacheFullFormTests : FullFormTest(ApacheBackend)
}
