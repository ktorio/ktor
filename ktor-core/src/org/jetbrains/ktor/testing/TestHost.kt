package org.jetbrains.ktor.testing

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.io.*
import kotlin.reflect.*

inline fun withApplication<reified T : Application>(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

fun withApplication(applicationClass: KClass<*>, test: TestApplicationHost.() -> Unit) {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.deployment.environment" to "test",
                    "ktor.application.class" to applicationClass.qualifiedName
                 ))
    val config = ApplicationConfig(testConfig, SL4JApplicationLog("<Test>"))
    val host = TestApplicationHost(config)
    host.test()
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

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): RequestResult {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}


class TestApplicationRequest(override val application: Application) : ApplicationRequest {
    override var requestLine: HttpRequestLine = HttpRequestLine(HttpMethod.Get, "/", "HTTP/1.1")

    var uri: String
        get() = requestLine.uri
        set(value) {
            requestLine = requestLine.copy(uri = value)
        }

    var method: HttpMethod
        get() = requestLine.method
        set(value) {
            requestLine = requestLine.copy(method = value)
        }

    override var body: String = ""

    override val parameters: Map<String, List<String>> get() {
        return queryParameters()
    }

    override val headers = hashMapOf<String, String>()

    var response: TestApplicationResponse? = null
    override fun respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        if (response != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = TestApplicationResponse()
        return response!!.handle()
    }
}

class TestApplicationResponse : ApplicationResponse {
    val headers = hashMapOf<String, String>()
    override fun header(name: String, value: String): ApplicationResponse {
        headers.put(name, value)
        return this
    }

    public var status: Int = 501
    override fun status(code: Int): ApplicationResponse {
        status = code
        return this
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
        val writer = StringWriter()
        writer.streamer()
        return content(writer.toString())
    }

    override fun send(): ApplicationRequestStatus {
        return ApplicationRequestStatus.Handled
    }
}

class TestApplication(config: ApplicationConfig) : Application(config)