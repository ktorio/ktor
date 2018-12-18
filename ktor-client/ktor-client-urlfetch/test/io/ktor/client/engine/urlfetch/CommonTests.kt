package io.ktor.client.engine.urlfetch

import io.ktor.client.tests.*
import org.junit.*

@Ignore
class UrlFetchCacheTest : CacheTest(UrlFetch)

class UrlFetchCookiesTest : CookiesTest(UrlFetch)

class UrlFetchAttributesTest : AttributesTest(UrlFetch)

class UrlFetchPostTest : PostTest(UrlFetch)

class UrlFetchMultithreadedTest : MultithreadedTest(UrlFetch)

class UrlFetchContentTest : ContentTest(UrlFetch)

class UrlFetchBuildersTest : BuildersTest(UrlFetch)

class UrlFetchFeaturesTest : FeaturesTest(UrlFetch)

class UrlFetchConnectionTest : ConnectionTest(UrlFetch)

class UrlFetchHttpClientTest : HttpClientTest(UrlFetch)
