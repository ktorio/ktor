package io.ktor.client.tests

import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.features.DefaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.port
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.http.HttpMethod
import io.ktor.http.fullPath
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class HttpClientTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/empty") {
                call.respondText("")
            }
            get("/hello") {
                call.respondText("hello")
            }
        }
    }

    @Test
    fun configCopiesOldFeaturesAndInterceptors() {
        val customFeatureKey = AttributeKey<Boolean>("customFeature")
        val anotherCustomFeatureKey = AttributeKey<Boolean>("anotherCustomFeature")

        val originalClient = HttpClient(factory, useDefaultTransformers = false) {
            install(DefaultRequest) {
                port = serverPort
                url.path("empty")
            }
            install("customFeature") {
                attributes.put(customFeatureKey, true)
            }
        }

        // check everything was installed in original
        val originalRequest = runBlocking {
            originalClient.execute(HttpRequestBuilder())
        }.request
        assertEquals("/empty", originalRequest.url.fullPath)

        assertTrue(originalClient.attributes.contains(customFeatureKey), "no custom feature installed")

        // create a new client, copying the original, with:
        // - a reconfigured DefaultRequest
        // - a new custom feature
        val newClient = originalClient.config {
            install(DefaultRequest) {
                port = serverPort
                url.path("hello")
            }
            install("anotherCustomFeature") {
                attributes.put(anotherCustomFeatureKey, true)
            }
        }

        // check the custom feature remained installed
        // and that we overrode the DefaultRequest
        val newRequest = runBlocking {
            newClient.execute(HttpRequestBuilder())
        }.request
        assertEquals("/hello", newRequest.url.fullPath)

        assertTrue(newClient.attributes.contains(customFeatureKey), "no custom feature installed")

        // check the new custom feature is there too
        assertTrue(newClient.attributes.contains(anotherCustomFeatureKey), "no other custom feature installed")
    }

}
