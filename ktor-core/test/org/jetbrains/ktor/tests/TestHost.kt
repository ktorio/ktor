package org.jetbrains.ktor.tests

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import java.io.*

fun createTestHost(): TestApplicationHost {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.deployment.environment" to "test",
                    "ktor.application.package" to "org.jetbrains.ktor.tests",
                    "ktor.application.class" to "org.jetbrains.ktor.tests.TestApplication"
                 ))
    val config = ApplicationConfig(testConfig, SL4JApplicationLog("<Test>"))
    return TestApplicationHost(config)
}

data class RequestResult(val requestResult: ApplicationRequestStatus, val response: TestApplicationResponse?)

class TestApplicationHost(val applicationConfig: ApplicationConfig) {
    val application: Application = ApplicationLoader(applicationConfig).application

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): RequestResult {
        val request = TestApplicationRequest(application)
        request.setup()
        val status = application.handle(request)
        return RequestResult(status, request.response)
    }
}

class TestApplicationRequest(override val application: Application) : ApplicationRequest {

    override var uri: String = "http://localhost/"
    override var httpMethod: String = "GET"
    override val parameters: Map<String, List<String>> get() {
        return queryParameters() + ("@method" to arrayListOf(httpMethod))
    }

    val headers = hashMapOf<String, String>()

    override fun header(name: String): String? = headers[name]
    override fun headers(): Map<String, String> = headers

    var response: TestApplicationResponse? = null
    override fun respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        if (response != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = TestApplicationResponse()
        return response!!.handle()
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

    public var content: String? = null
    override fun content(text: String, encoding: String): ApplicationResponse {
        content = text
        return this
    }

    override fun content(bytes: ByteArray): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
        throw UnsupportedOperationException()
    }

    override fun send(): ApplicationRequestStatus {
        return ApplicationRequestStatus.Handled
    }

    override fun sendRedirect(url: String): ApplicationRequestStatus {
        return ApplicationRequestStatus.Handled
    }
}

class TestApplication(config: ApplicationConfig) : Application(config)