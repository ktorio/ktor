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
}

class TestApplicationResponse : ApplicationResponse {

    private val headers = hashMapOf<String, String>()
    private var statusCode: Int? = null

    private val header = Interceptable2<String, String, Unit> { name, value -> headers.put(name, value) }
    private val status = Interceptable1<Int, Unit> { code -> this.statusCode = code }
    private val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteArrayOutputStream()
        stream.body()
        content = stream.toString()
        ApplicationRequestStatus.Handled
    }
    private val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    public override fun header(name: String): String? = headers.get(name)
    public override fun header(name: String, value: String) = header.call(name, value)
    public override fun interceptHeader(handler: (String, String, (String, String) -> Unit) -> Unit) = header.intercept(handler)

    public override fun status(): Int? = statusCode
    public override fun status(value: Int) = status.call(value)
    public override fun interceptStatus(handler: (Int, (Int) -> Unit) -> Unit) = status.intercept(handler)

    public var content: String? = null

    override fun send(message: Any): ApplicationRequestStatus = send.call(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus) = send.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.call(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)
}

class TestApplication(config: ApplicationConfig) : Application(config)