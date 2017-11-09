package io.ktor.client.backend.cio

import io.ktor.client.tests.*
import org.junit.experimental.runners.*
import org.junit.runner.*

@RunWith(Enclosed::class)
class CIOClientTestSuite {
    class CIOCacheTest : CacheTest(CIOBackend)

    class CIOCookiesTest : CookiesTest(CIOBackend)

    class CIOFullFormTests : FullFormTest(CIOBackend)

    class CIOPostTests : PostTest(CIOBackend)
}
