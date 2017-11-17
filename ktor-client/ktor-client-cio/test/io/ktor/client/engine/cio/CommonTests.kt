package io.ktor.client.engine.cio

import io.ktor.client.tests.*
import org.junit.experimental.runners.*
import org.junit.runner.*


@RunWith(Enclosed::class)
class CIOClientTestSuite {
    class CIOCacheTest : CacheTest(CIO)

    class CIOCookiesTest : CookiesTest(CIO)

    class CIOFullFormTest : FullFormTest(CIO)

    class CIOPostTest : PostTest(CIO)
}
