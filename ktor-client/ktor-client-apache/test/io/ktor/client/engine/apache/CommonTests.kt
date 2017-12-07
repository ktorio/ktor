package io.ktor.client.engine.apache

import io.ktor.client.tests.*
import org.junit.*


@Ignore
class ApacheCacheTest : CacheTest(Apache)

class ApacheCookiesTest : CookiesTest(Apache)

class ApachePostTest : PostTest(Apache)

class ApacheMultithreadedTest : MultithreadedTest(Apache)

class ApacheFullFormTest : FullFormTest(Apache)
