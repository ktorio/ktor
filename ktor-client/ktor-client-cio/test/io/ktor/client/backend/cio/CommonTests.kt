package io.ktor.client.backend.cio

import io.ktor.client.tests.*
import org.junit.*
import org.junit.experimental.runners.*
import org.junit.runner.*

private val BACKEND_FACTORY = CIOBackend

@RunWith(Enclosed::class)
class CIOClientTestSuite {
    class CIOCacheTest : CacheTests(BACKEND_FACTORY)

    class CIOCookiesTest : CookiesTests(BACKEND_FACTORY)

    @Ignore("Redirect is currently not supported in CIO backend")
    class CIOFollowRedirectsTest : FollowRedirectsTest(BACKEND_FACTORY)

    class CIOFullFormTests : FullFormTests(BACKEND_FACTORY)

    @Ignore
    class CIOPostTests : PostTests(BACKEND_FACTORY)
}
