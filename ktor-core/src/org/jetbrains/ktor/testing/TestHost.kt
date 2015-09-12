package org.jetbrains.ktor.testing

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import java.util.*
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

data class RequestResult(val requestResult: ApplicationRequestStatus, val response: TestApplicationResponse)

class TestApplicationHost(val applicationConfig: ApplicationConfig) {
    val application: Application = ApplicationLoader(applicationConfig).application

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): RequestResult {
        val request = TestApplicationRequest()
        request.setup()
        val context = TestApplicationRequestContext(application, request)
        val status = application.handle(context)
        context.close()
        return RequestResult(status, context.response)
    }
}

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): RequestResult {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}

class TestApplicationRequestContext(override val application: Application, override val request: TestApplicationRequest) : ApplicationRequestContext {
    override val close = Interceptable0<Unit> {}

    override val response = TestApplicationResponse()
}

class TestApplicationRequest() : ApplicationRequest {
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

    override val parameters: ValuesMap get() {
        return queryParameters()
    }

    private val headersMap = hashMapOf<String, ArrayList<String>>()
    fun addHeader(name: String, value: String) {
        headersMap.getOrPut(name, { arrayListOf() }).add(value)
    }

    override val headers = ValuesMap(headersMap)

    override val attributes = Attributes()
    override val cookies = RequestCookies(this)
}

class TestApplicationResponse : ApplicationResponse {
    private var statusCode: HttpStatusCode? = null

    private val status = Interceptable1<HttpStatusCode, Unit> { code -> this.statusCode = code }
    private val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteArrayOutputStream()
        stream.body()
        content = stream.toString()
        ApplicationRequestStatus.Handled
    }
    private val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    override val headers: ResponseHeaders = object: ResponseHeaders() {
        private val headersMap = HashMap<String, MutableList<String>>()

        override fun hostAppendHeader(name: String, value: String) {
            headersMap.getOrPut(name) { ArrayList() }.add(value)
        }

        override fun getHostHeaderNames(): List<String> = headersMap.keySet().toList()
        override fun getHostHeaderValues(name: String): List<String> = headersMap[name] ?: emptyList()
    }
    override val cookies = ResponseCookies(this)

    public override fun status(): HttpStatusCode? = statusCode
    public override fun status(value: HttpStatusCode) = status.call(value)
    public override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    public var content: String? = null

    override fun send(message: Any): ApplicationRequestStatus = send.call(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus) = send.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.call(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)
}

class TestApplication(config: ApplicationConfig) : Application(config)