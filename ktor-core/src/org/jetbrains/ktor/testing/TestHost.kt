package org.jetbrains.ktor.testing

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

inline fun <reified T : Application> withApplication(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

fun withApplication(applicationClass: KClass<*>, test: TestApplicationHost.() -> Unit) {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.deployment.environment" to "test",
                    "ktor.application.class" to applicationClass.jvmName
            ))
    val config = HoconApplicationConfig(testConfig, ApplicationConfig::class.java.classLoader, SLF4JApplicationLog("ktor.test"))
    val host = TestApplicationHost(config)
    host.test()
}

class TestApplicationHost(val applicationConfig: ApplicationConfig) {
    val application: Application = ApplicationLoader(applicationConfig).application

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val request = TestApplicationRequest()
        request.setup()
        val call = TestApplicationCall(application, request)
        application.handle(call)
        return call
    }
}

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}

class TestApplicationCall(application: Application, override val request: TestApplicationRequest) : BaseApplicationCall(application) {
    override val parameters: ValuesMap get() = request.parameters
    override val attributes = Attributes()
    override val close = Interceptable0 {
        requestResult = ApplicationCallResult.Handled
    }

    override val response = TestApplicationResponse()

    var requestResult = ApplicationCallResult.Unhandled
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

    var body: String = ""
    var multiPartEntries: List<PartData> = emptyList()

    override val parameters: ValuesMap get() {
        return queryParameters() + if (contentType().match(ContentType.Application.FormUrlEncoded)) body.parseUrlEncodedParameters() else ValuesMap.Empty
    }

    private var headersMap: MutableMap<String, MutableList<String>>? = hashMapOf()
    fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name, { arrayListOf() }).add(value)
    }

    override val headers by lazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        ValuesMapImpl(map, caseInsensitiveKey = true)
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getMultiPartData(): MultiPartData = object : MultiPartData {
            override val parts: Sequence<PartData>
                get() = when {
                    isMultipart() -> multiPartEntries.asSequence()
                    else -> throw IOException("The request content is not multipart encoded")
                }
        }
    }

    override val cookies = RequestCookies(this)
}

class TestApplicationResponse() : BaseApplicationResponse() {
    private var statusCode: HttpStatusCode? = null

    override val status = Interceptable1<HttpStatusCode, Unit> { code -> this.statusCode = code }
    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteArrayOutputStream()
        stream.body()
        byteContent = stream.toByteArray()
        content = stream.toString()
        ApplicationCallResult.Handled
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val headersMap = HashMap<String, MutableList<String>>()

        override fun hostAppendHeader(name: String, value: String) {
            headersMap.getOrPut(name) { ArrayList() }.add(value)
        }

        override fun getHostHeaderNames(): List<String> = headersMap.keys.toList()
        override fun getHostHeaderValues(name: String): List<String> = headersMap[name] ?: emptyList()
    }

    override fun status(): HttpStatusCode? = statusCode

    var content: String? = null
    var byteContent: ByteArray? = null
}

class TestApplication(config: ApplicationConfig) : Application(config)