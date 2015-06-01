package ktor.tests

import com.typesafe.config.*
import ktor.application.*
import java.io.*

fun createTestHost(): TestApplicationHost {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.environment" to "test",
                    "ktor.application.package" to "ktor.tests",
                    "ktor.application.class" to "ktor.tests.TestApplication"
                 ))
    val config = ApplicationConfig(testConfig, SL4JApplicationLog("<Test>"))
    return TestApplicationHost(config)
}

data class RequestResult(val handled: Boolean, val response: TestApplicationResponse?)

class TestApplicationHost(val applicationConfig: ApplicationConfig) {
    val application: Application = ApplicationLoader(applicationConfig).application

    fun makeRequest(setup: TestApplicationRequest.() -> Unit): RequestResult {
        val request = TestApplicationRequest(application)
        request.setup()
        val result = application.handle(request)
        return RequestResult(result, request.response)
    }
}

class TestApplicationRequest(override val application: Application) : ApplicationRequest {

    override var uri: String = "http://localhost/"
    override var httpMethod: String = "GET"
    override val parameters: Map<String, List<String>> get() = queryParameters()

    var response: TestApplicationResponse? = null
    val headers = hashMapOf<String, String>()

    override fun header(name: String): String? = headers[name]
    override fun headers(): Map<String, String> = headers

    override fun hasResponse(): Boolean = response != null


    override fun response(): ApplicationResponse {
        if (response != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = TestApplicationResponse()
        return response!!
    }

    override fun response(body: ApplicationResponse.() -> Unit): ApplicationResponse {
        val response = response()
        response.body()
        return response
    }
}

class TestApplicationResponse : ApplicationResponse {
    override fun header(name: String, value: String): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun header(name: String, value: Int): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    public var status: Int = 0
    override fun status(code: Int): ApplicationResponse {
        status = code
        return this
    }

    override fun contentType(value: String): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun content(text: String, encoding: String): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun content(bytes: ByteArray): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun send() {
        throw UnsupportedOperationException()
    }

    override fun sendRedirect(url: String) {
        throw UnsupportedOperationException()
    }
}

class TestApplication(config: ApplicationConfig) : Application(config)