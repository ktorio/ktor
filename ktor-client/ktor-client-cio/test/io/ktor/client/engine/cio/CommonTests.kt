package io.ktor.client.engine.cio

import io.ktor.client.tests.*
import org.junit.*
import org.junit.experimental.runners.*
import org.junit.runner.*


@RunWith(Enclosed::class)
class CIOClientTestSuite {
    @Ignore
    class CIOCacheTest : CacheTest(CIO)

    @Ignore
    class CIOCookiesTest : CookiesTest(CIO)

    class CIOPostTest : PostTest(CIO)
}

class CIOFullFormTest : FullFormTest(CIO)

