package io.ktor.client.engine.cio

import io.ktor.client.tests.*
import org.junit.*


class CIOCookiesTest : CookiesTest(CIO)

class CIOAttributesTest : AttributesTest(CIO)

class CIOPostTest : PostTest(CIO)

class CIOFullFormTest : FullFormTest(CIO)

class CIOMultithreadedTest : MultithreadedTest(CIO)

class CIOContentTest : ContentTest(CIO)

class CIORedirectTest : HttpRedirectTest(CIO)

class CIOBuildersTest : BuildersTest(CIO)

class CIOFeaturesTest : FeaturesTest(CIO)

class CIOConnectionTest : ConnectionTest(CIO)

class CIOHttpClientTest : HttpClientTest(CIO)
