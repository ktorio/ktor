package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
abstract class HttpClientTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
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

        val originalClient = HttpClient(factory) {
            useDefaultTransformers = false

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
        // and that we override the DefaultRequest
        val newRequest = runBlocking {
            newClient.execute(HttpRequestBuilder())
        }.request
        assertEquals("/hello", newRequest.url.fullPath)

        assertTrue(newClient.attributes.contains(customFeatureKey), "no custom feature installed")

        // check the new custom feature is there too
        assertTrue(newClient.attributes.contains(anotherCustomFeatureKey), "no other custom feature installed")
    }

}
