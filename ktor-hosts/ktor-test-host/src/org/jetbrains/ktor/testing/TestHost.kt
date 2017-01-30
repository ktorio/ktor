package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

@Deprecated("Use withTestApplication instead once you migrate to module functions")
inline fun <reified T : Application> withApplication(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

@Deprecated("Use withTestApplication once you migrate to module functions")
inline fun <reified T : ApplicationFeature<*, *, *>> withApplicationFeature(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

fun withApplication(environment: ApplicationEnvironment = emptyTestEnvironment(), test: TestApplicationHost.() -> Unit) {
    val host = TestApplicationHost(environment)
    try {
        host.test()
    } finally {
        host.dispose()
    }
}

fun withApplication(applicationClass: KClass<*>, test: TestApplicationHost.() -> Unit) {
    val config = MapApplicationConfig(
            "ktor.deployment.environment" to "test",
            "ktor.application.class" to applicationClass.jvmName
    )
    val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, SLF4JApplicationLog("ktor.test"), config)
    withApplication(environment, test)
}

fun emptyTestEnvironment(): ApplicationEnvironment {
    val config = MapApplicationConfig("ktor.deployment.environment" to "test")
    return BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, SLF4JApplicationLog("ktor.test"), config)
}

class TestApplicationHost(val environment: ApplicationEnvironment = emptyTestEnvironment()) {
    private val applicationLoader = ApplicationLoader(environment, false)

    init {
        applicationLoader.onBeforeInitializeApplication {
            install(TransformationSupport).registerDefaultHandlers()
        }
    }

    val application: Application = applicationLoader.application
    private val pipeline = ApplicationCallPipeline()

    init {
        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.response.pipeline.intercept(RespondPipeline.Before) {
                proceed()
                (call as? TestApplicationCall)?.requestHandled = true
            }

            application.execute(call)
        }
    }

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall(setup)
        runBlocking {
            pipeline.execute(call)
        }
        return call
    }

    fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, encodeBase64("test".toByteArray()))

            setup()
        }

        runBlocking(Here) { pipeline.execute(call) }

        return call
    }

    fun dispose() {
        application.dispose()
    }

    private fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val request = TestApplicationRequest()
        setup(request)

        return TestApplicationCall(application, request)
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
    suspend override fun respond(message: Any) {
        super.respond(message)
        response.close()
    }

    override val response = TestApplicationResponse(this, respondPipeline)

    @Volatile
    var requestHandled = false

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        commitHeaders(upgrade)
        upgrade.upgrade(this@TestApplicationCall, this, request.content.get(), response.realContent.value)
    }

    override fun responseChannel(): WriteChannel = response.realContent.value.apply {
        response.headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
            val contentLength = contentLengthString.toLong()
            if (contentLength >= Int.MAX_VALUE) {
                throw IllegalStateException("Content length is too big for test host")
            }

            ensureCapacity(contentLength.toInt())
        }
    }
}

class TestApplicationRequest(
        var method: HttpMethod = HttpMethod.Get,
        var uri: String = "/",
        var version: String = "HTTP/1.1"
) : ApplicationRequest {

    @Deprecated("Use primary constructor instead as HttpRequestLine is deprecated", level = DeprecationLevel.ERROR)
    constructor(requestLine: @Suppress("DEPRECATION") HttpRequestLine) : this(requestLine.method, requestLine.uri, requestLine.version)

    override val attributes = Attributes()

    var protocol: String = "http"

    override val local = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        override val port: Int
            get() = header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt() ?: 80

        override val host: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: "localhost"

        override val remoteHost: String
            get() = "localhost"

        override val version: String
            get() = this@TestApplicationRequest.version
    }

    var bodyBytes: ByteArray = ByteArray(0)
    var body: String
        get() = bodyBytes.toString(Charsets.UTF_8)
        set(newValue) {
            bodyBytes = newValue.toByteArray(Charsets.UTF_8)
        }

    var multiPartEntries: List<PartData> = emptyList()

    override val queryParameters by lazy { parseQueryString(queryString()) }

    private var headersMap: MutableMap<String, MutableList<String>>? = hashMapOf()
    fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name, { arrayListOf() }).add(value)
    }

    override val headers by lazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        valuesOf(map, caseInsensitiveKey = true)
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
        override fun getReadChannel() = ByteBufferReadChannel(bodyBytes)

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

class TestApplicationResponse(call: ApplicationCall, respondPipeline: RespondPipeline = RespondPipeline()) : BaseApplicationResponse(call, respondPipeline) {
    internal val realContent = lazy { ByteBufferWriteChannel() }

    @Volatile
    private var closed = false

    override fun setStatus(statusCode: HttpStatusCode) {
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val headersMap = ValuesMapBuilder(true)
        private val headers: ValuesMap by lazy { headersMap.build() }

        override fun hostAppendHeader(name: String, value: String) {
            if (closed)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            headersMap.append(name, value)
        }

        override fun getHostHeaderNames(): List<String> = headers.names().toList()
        override fun getHostHeaderValues(name: String): List<String> = headers.getAll(name).orEmpty()
    }


    val content: String?
        get() = if (realContent.isInitialized()) {
            realContent.value.toString(headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8)
        } else {
            null
        }

    val byteContent: ByteArray?
        get() = if (realContent.isInitialized()) {
            realContent.value.toByteArray()
        } else {
            null
        }

    fun close() {
        closed = true
    }
}

@Deprecated("You actually don't need to refer to this class. Use withTestApplication to eliminate it.")
class TestApplication : Application(emptyTestEnvironment())
