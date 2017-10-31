package io.ktor.client.backend.apache

import io.ktor.client.tests.*
import org.junit.experimental.runners.*
import org.junit.runner.*


private val BACKEND_FACTORY = ApacheBackend

@RunWith(Enclosed::class)
class ApacheClientTestSuite {
    class ApacheCacheTest : CacheTests(BACKEND_FACTORY)

    class ApacheCookiesTest : CookiesTests(BACKEND_FACTORY)

    class ApacheFollowRedirectsTest : FollowRedirectsTest(BACKEND_FACTORY)

    class ApacheFullFormTests : FullFormTests(BACKEND_FACTORY)

    class ApachePostTests : PostTests(BACKEND_FACTORY)

    class ApacheMultithreadedTest : MultithreadedTest(BACKEND_FACTORY)
}
