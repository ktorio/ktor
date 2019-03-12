package io.ktor.client.engine.okhttp

import io.ktor.client.tests.*
import org.junit.*

class OkHttpCookiesTest : CookiesTest(OkHttp)

class OkHttpAttributesTest : AttributesTest(OkHttp)

class OkHttpPostTest : PostTest(OkHttp)

@Ignore
class OkHttpMultithreadedTest : MultithreadedTest(OkHttp)

class OkHttpContentTest : ContentTest(OkHttp)

class OkHttpBuildersTest : BuildersTest(OkHttp)

class OkHttpFeaturesTest : FeaturesTest(OkHttp)

class OkHttpConnectionTest : ConnectionTest(OkHttp)

class OkHttpHttpClientTest : HttpClientTest(OkHttp)
