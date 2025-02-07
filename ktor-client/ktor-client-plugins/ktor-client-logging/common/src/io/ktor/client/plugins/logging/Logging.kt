/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.isSaved
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

private val ClientCallLogger = AttributeKey<HttpClientCallLogger>("CallLogger")
private val DisableLogging = AttributeKey<Unit>("DisableLogging")

public enum class LoggingFormat {
    Default,

    /**
     * [OkHttp logging format](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp-logging-interceptor/src/main/kotlin/okhttp3/logging/HttpLoggingInterceptor.kt#L48-L105).
     * Writes only application-level logs because the low-level HTTP communication is hidden within the engine implementations.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingFormat.OkHttp)
     */
    OkHttp
}

/**
 * A configuration for the [Logging] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig)
 */
@KtorDsl
public class LoggingConfig {
    internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal val sanitizedHeaders = mutableListOf<SanitizedHeader>()

    private var _logger: Logger? = null

    /**
     * A general format for logging requests and responses. See [LoggingFormat].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.format)
     */
    public var format: LoggingFormat = LoggingFormat.Default

    /**
     * Specifies a [Logger] instance.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.logger)
     */
    public var logger: Logger
        get() = _logger ?: Logger.DEFAULT
        set(value) {
            _logger = value
        }

    /**
     * Specifies the logging level.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.level)
     */
    public var level: LogLevel = LogLevel.HEADERS

    /**
     * Allows you to filter log messages for calls matching a [predicate].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.filter)
     */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
     * In the example below, Authorization header value will be replaced with '***' when logging:
     * ```kotlin
     * sanitizeHeader { header -> header == HttpHeaders.Authorization }
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.sanitizeHeader)
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        sanitizedHeaders.add(SanitizedHeader(placeholder, predicate))
    }
}

/**
 * A client's plugin that provides the capability to log HTTP calls.
 *
 * You can learn more from [Logging](https://ktor.io/docs/client-logging.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.Logging)
 */
@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
public val Logging: ClientPlugin<LoggingConfig> = createClientPlugin("Logging", ::LoggingConfig) {
    val logger: Logger = pluginConfig.logger
    val level: LogLevel = pluginConfig.level
    val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
    val sanitizedHeaders: List<SanitizedHeader> = pluginConfig.sanitizedHeaders
    val okHttpFormat = pluginConfig.format == LoggingFormat.OkHttp

    fun shouldBeLogged(request: HttpRequestBuilder): Boolean = filters.isEmpty() || filters.any { it(request) }

    fun isNone(): Boolean = level == LogLevel.NONE
    fun isInfo(): Boolean = level == LogLevel.INFO
    fun isHeaders(): Boolean = level == LogLevel.HEADERS
    fun isBody(): Boolean = level == LogLevel.BODY || level == LogLevel.ALL

    /**
     * Detects if the body is a binary data
     * @return
     * Boolean: true if the body is a binary data.
     * Long?: body size if calculated.
     * ByteReadChannel: body channel with the original data.
     */
    suspend fun detectIfBinary(
        body: ByteReadChannel,
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers
    ): Triple<Boolean, Long?, ByteReadChannel> {
        if (headers.contains(HttpHeaders.ContentEncoding)) {
            return Triple(true, contentLength, body)
        }

        val charset = if (contentType != null) {
            contentType.charset() ?: Charsets.UTF_8
        } else {
            Charsets.UTF_8
        }

        var isBinary = false
        val firstChunk = ByteArray(1024)
        val firstReadSize = body.readAvailable(firstChunk)

        if (firstReadSize < 1) {
            return Triple(false, 0L, body)
        }

        val buffer = Buffer().apply { writeFully(firstChunk, 0, firstReadSize) }
        val firstChunkText = charset.newDecoder().decode(buffer, firstReadSize)

        var lastCharIndex = -1
        for (ch in firstChunkText) {
            lastCharIndex += 1
        }

        for ((i, ch) in firstChunkText.withIndex()) {
            if (ch == '\ufffd' && i != lastCharIndex) {
                isBinary = true
                break
            }
        }

        if (!isBinary) {
            val channel = ByteChannel()

            val copied = client.async {
                channel.writeFully(firstChunk, 0, firstReadSize)
                val copied = body.copyTo(channel)
                channel.flushAndClose()
                copied
            }.await()

            return Triple(isBinary, copied + firstReadSize, channel)
        }

        return Triple(isBinary, contentLength, body)
    }

    suspend fun logRequestBody(
        content: OutgoingContent,
        contentLength: Long?,
        headers: Headers,
        method: HttpMethod,
        body: ByteReadChannel
    ) {
        val (isBinary, size, newBody) = detectIfBinary(body, contentLength, content.contentType, headers)

        if (!isBinary) {
            val contentType = content.contentType
            val charset = if (contentType != null) {
                contentType.charset() ?: Charsets.UTF_8
            } else {
                Charsets.UTF_8
            }

            logger.log(newBody.readRemaining().readText(charset = charset))
            logger.log("--> END ${method.value} ($size-byte body)")
        } else {
            var type = "binary"
            if (headers.contains(HttpHeaders.ContentEncoding)) {
                type = "encoded"
            }

            if (size != null) {
                logger.log("--> END ${method.value} ($type $size-byte body omitted)")
            } else {
                logger.log("--> END ${method.value} ($type body omitted)")
            }
        }
    }

    suspend fun logOutgoingContent(
        content: OutgoingContent,
        method: HttpMethod,
        headers: Headers,
        process: (ByteReadChannel) -> ByteReadChannel = { it }
    ): OutgoingContent? {
        return when (content) {
            is OutgoingContent.ByteArrayContent -> {
                val bytes = content.bytes()
                logRequestBody(content, bytes.size.toLong(), headers, method, ByteReadChannel(bytes))
                null
            }
            is OutgoingContent.ContentWrapper -> {
                logOutgoingContent(content.delegate(), method, headers, process)
            }
            is OutgoingContent.NoContent -> {
                logger.log("--> END ${method.value}")
                null
            }
            is OutgoingContent.ProtocolUpgrade -> {
                logger.log("--> END ${method.value}")
                null
            }
            is OutgoingContent.ReadChannelContent -> {
                val (origChannel, newChannel) = content.readFrom().split(client)
                logRequestBody(content, content.contentLength, headers, method, newChannel)
                LoggedContent(content, origChannel)
            }
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel()

                client.launch {
                    content.writeTo(channel)
                    channel.close()
                }

                val (origChannel, newChannel) = channel.split(client)
                logRequestBody(content, content.contentLength, headers, method, newChannel)
                LoggedContent(content, origChannel)
            }
        }
    }

    suspend fun logRequestOkHttpFormat(request: HttpRequestBuilder): OutgoingContent? {
        if (isNone()) return null

        val uri = URLBuilder().takeFrom(request.url).build().pathQuery()
        val body = request.body
        val headers = HeadersBuilder().apply {
            if (body is OutgoingContent &&
                request.method != HttpMethod.Get &&
                request.method != HttpMethod.Head &&
                body !is EmptyContent
            ) {
                body.contentType?.let {
                    appendIfNameAbsent(HttpHeaders.ContentType, it.toString())
                }
                body.contentLength?.let {
                    appendIfNameAbsent(HttpHeaders.ContentLength, it.toString())
                }
            }
            appendAll(request.headers)
        }.build()

        val contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val startLine = when {
            (request.method == HttpMethod.Get) ||
                (request.method == HttpMethod.Head) ||
                ((isHeaders() || isBody()) && contentLength != null) ||
                (isHeaders() && contentLength == null) ||
                headers.contains(HttpHeaders.ContentEncoding) -> "--> ${request.method.value} $uri"

            isInfo() && contentLength != null -> "--> ${request.method.value} $uri ($contentLength-byte body)"

            body is OutgoingContent.WriteChannelContent ||
                body is OutgoingContent.ReadChannelContent -> "--> ${request.method.value} $uri (unknown-byte body)"

            else -> {
                val size = computeRequestBodySize(request.body, headers)
                "--> ${request.method.value} $uri ($size-byte body)"
            }
        }

        logger.log(startLine)

        if (!isHeaders() && !isBody()) {
            return null
        }

        for ((name, values) in headers.entries()) {
            if (sanitizedHeaders.find { sh -> sh.predicate(name) } == null) {
                logger.log("$name: ${values.joinToString(separator = ", ")}")
            } else {
                logger.log("$name: ██")
            }
        }

        if (!isBody() || request.method == HttpMethod.Get || request.method == HttpMethod.Head) {
            logger.log("--> END ${request.method.value}")
            return null
        }

        logger.log("")

        if (body !is OutgoingContent) {
            logger.log("--> END ${request.method.value}")
            return null
        }

        val newContent = if (request.headers[HttpHeaders.ContentEncoding] == "gzip") {
            logOutgoingContent(body, request.method, headers) { channel ->
                GZipEncoder.decode(channel)
            }
        } else {
            logOutgoingContent(body, request.method, headers)
        }

        return newContent
    }

    suspend fun logResponseBody(response: HttpResponse, body: ByteReadChannel) {
        logger.log("")

        val (isBinary, size, newBody) = detectIfBinary(
            body,
            response.contentLength(),
            response.contentType(),
            response.headers
        )
        val duration = response.responseTime.timestamp - response.requestTime.timestamp

        if (size == 0L) {
            logger.log("<-- END HTTP (${duration}ms, $size-byte body)")
            return
        }

        if (!isBinary) {
            val contentType = response.contentType()
            val charset = if (contentType != null) {
                contentType.charset() ?: Charsets.UTF_8
            } else {
                Charsets.UTF_8
            }

            logger.log(newBody.readRemaining().readText(charset = charset))
            logger.log("<-- END HTTP (${duration}ms, $size-byte body)")
        } else {
            var type = "binary"
            if (response.headers.contains(HttpHeaders.ContentEncoding)) {
                type = "encoded"
            }

            if (size != null) {
                logger.log("<-- END HTTP (${duration}ms, $type $size-byte body omitted)")
            } else {
                logger.log("<-- END HTTP (${duration}ms, $type body omitted)")
            }
        }
    }

    suspend fun logResponseOkHttpFormat(response: HttpResponse): HttpResponse {
        if (isNone()) return response

        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val request = response.request
        val duration = response.responseTime.timestamp - response.requestTime.timestamp

        val startLine = when {
            response.headers[HttpHeaders.TransferEncoding] == "chunked" &&
                (isInfo() || isHeaders()) ->
                "<-- ${response.status} ${request.url.pathQuery()} (${duration}ms, unknown-byte body)"

            isInfo() && contentLength != null ->
                "<-- ${response.status} ${request.url.pathQuery()} (${duration}ms, $contentLength-byte body)"

            isBody() ||
                (isInfo() && contentLength == null) ||
                (isHeaders() && contentLength != null) ||
                (response.headers[HttpHeaders.ContentEncoding] == "gzip") ->
                "<-- ${response.status} ${request.url.pathQuery()} (${duration}ms)"

            else -> "<-- ${response.status} ${request.url.pathQuery()} (${duration}ms, unknown-byte body)"
        }

        logger.log(startLine)

        if (!isHeaders() && !isBody()) {
            return response
        }

        for ((name, values) in response.headers.entries()) {
            if (sanitizedHeaders.find { sh -> sh.predicate(name) } == null) {
                logger.log("$name: ${values.joinToString(separator = ", ")}")
            } else {
                logger.log("$name: ██")
            }
        }

        if (!isBody()) {
            logger.log("<-- END HTTP")
            return response
        }

        if (contentLength != null && contentLength == 0L) {
            logger.log("<-- END HTTP (${duration}ms, $contentLength-byte body)")
            return response
        }

        if (response.contentType() == ContentType.Text.EventStream) {
            logger.log("<-- END HTTP (streaming)")
            return response
        }

        if (response.isSaved) {
            logResponseBody(response, response.rawContent)
            return response
        }

        val (origChannel, newChannel) = response.rawContent.split(response)

        logResponseBody(response, newChannel)

        val call = response.call.wrapWithContent(origChannel)
        return call.response
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun logRequestBody(
        content: OutgoingContent,
        logger: HttpClientCallLogger
    ): OutgoingContent {
        val requestLog = StringBuilder()
        requestLog.appendLine("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Default + MDCContext()) {
            try {
                val text = channel.tryReadText(charset) ?: "[request body omitted]"
                requestLog.appendLine("BODY START")
                requestLog.appendLine(text)
                requestLog.append("BODY END")
            } finally {
                logger.logRequest(requestLog.toString())
                logger.closeRequestLog()
            }
        }

        return content.observe(channel)
    }

    fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
        }
    }

    suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        val content = request.body as OutgoingContent
        val callLogger = HttpClientCallLogger(logger)
        request.attributes.put(ClientCallLogger, callLogger)

        val message = buildString {
            if (level.info) {
                appendLine("REQUEST: ${Url(request.url)}")
                appendLine("METHOD: ${request.method}")
            }

            if (level.headers) {
                appendLine("COMMON HEADERS")
                logHeaders(request.headers.entries(), sanitizedHeaders)

                appendLine("CONTENT HEADERS")
                val contentLengthPlaceholder = sanitizedHeaders
                    .firstOrNull { it.predicate(HttpHeaders.ContentLength) }
                    ?.placeholder
                val contentTypePlaceholder = sanitizedHeaders
                    .firstOrNull { it.predicate(HttpHeaders.ContentType) }
                    ?.placeholder
                content.contentLength?.let {
                    logHeader(HttpHeaders.ContentLength, contentLengthPlaceholder ?: it.toString())
                }
                content.contentType?.let {
                    logHeader(HttpHeaders.ContentType, contentTypePlaceholder ?: it.toString())
                }
                logHeaders(content.headers.entries(), sanitizedHeaders)
            }
        }

        if (message.isNotEmpty()) {
            callLogger.logRequest(message)
        }

        if (message.isEmpty() || !level.body) {
            callLogger.closeRequestLog()
            return null
        }

        return logRequestBody(content, callLogger)
    }

    fun logResponseException(log: StringBuilder, request: HttpRequest, cause: Throwable) {
        if (!level.info) return
        log.append("RESPONSE ${request.url} failed with exception: $cause")
    }

    on(SendHook) { request ->
        if (!shouldBeLogged(request)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
        }

        if (okHttpFormat) {
            val content = logRequestOkHttpFormat(request)

            try {
                if (content != null) {
                    proceedWith(content)
                } else {
                    proceed()
                }
            } catch (cause: Throwable) {
                logger.log("<-- HTTP FAILED: $cause")
                throw cause
            }

            return@on
        }

        val loggedRequest = try {
            logRequest(request)
        } catch (_: Throwable) {
            null
        }

        try {
            proceedWith(loggedRequest ?: request.body)
        } catch (cause: Throwable) {
            logRequestException(request, cause)
            throw cause
        } finally {
        }
    }

    on(ResponseAfterEncodingHook) { response ->
        if (okHttpFormat) {
            val newResponse = logResponseOkHttpFormat(response)
            if (newResponse != response) {
                proceedWith(newResponse)
            }
        }
    }

    on(ResponseHook) { response ->
        if (okHttpFormat) return@on

        if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) return@on

        val callLogger = response.call.attributes[ClientCallLogger]
        val header = StringBuilder()

        var failed = false
        try {
            logResponseHeader(header, response.call.response, level, sanitizedHeaders)
            proceed()
        } catch (cause: Throwable) {
            logResponseException(header, response.call.request, cause)
            failed = true
            throw cause
        } finally {
            callLogger.logResponseHeader(header.toString())
            if (failed || !level.body) callLogger.closeResponseLog()
        }
    }

    on(ReceiveHook) { call ->
        if (okHttpFormat) return@on

        if (level == LogLevel.NONE || call.attributes.contains(DisableLogging)) {
            return@on
        }

        try {
            proceed()
        } catch (cause: Throwable) {
            val log = StringBuilder()
            val callLogger = call.attributes[ClientCallLogger]
            logResponseException(log, call.request, cause)
            callLogger.logResponseException(log.toString())
            callLogger.closeResponseLog()
            throw cause
        }
    }

    if (okHttpFormat) return@createClientPlugin

    if (!level.body) return@createClientPlugin

    @OptIn(InternalAPI::class)
    val observer: ResponseHandler = observer@{
        if (level == LogLevel.NONE || it.call.attributes.contains(DisableLogging)) {
            return@observer
        }

        val callLogger = it.call.attributes[ClientCallLogger]
        val log = StringBuilder()
        try {
            logResponseBody(log, it.contentType(), it.rawContent)
        } catch (_: Throwable) {
        } finally {
            callLogger.logResponseBody(log.toString().trim())
            callLogger.closeResponseLog()
        }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
}

private fun Url.pathQuery(): String {
    return buildString {
        if (encodedPath.isEmpty()) {
            append("/")
        } else {
            append(encodedPath)
        }

        if (!encodedQuery.isEmpty()) {
            append("?")
            append(encodedQuery)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun computeRequestBodySize(content: Any, headers: Headers): Long {
    check(content is OutgoingContent)

    return when (content) {
        is OutgoingContent.ByteArrayContent -> content.bytes().size.toLong()
        is OutgoingContent.ContentWrapper -> computeRequestBodySize(content.delegate(), content.headers)
        is OutgoingContent.NoContent -> 0
        is OutgoingContent.ProtocolUpgrade -> 0
        else -> error("Unable to calculate the size for type ${content::class.simpleName}")
    }
}

/**
 * Configures and installs [Logging] in [HttpClient].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.Logging)
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.Logging(block: LoggingConfig.() -> Unit = {}) {
    install(Logging, block)
}

internal class SanitizedHeader(
    val placeholder: String,
    val predicate: (String) -> Boolean
)

private object ResponseHook : ClientHook<suspend ResponseHook.Context.(response: HttpResponse) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponse, Unit>) {
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(response: HttpResponse) -> Unit
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            handler(Context(this), subject)
        }
    }
}

private object ResponseAfterEncodingHook :
    ClientHook<suspend ResponseAfterEncodingHook.Context.(response: HttpResponse) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponse, Unit>) {
        suspend fun proceedWith(response: HttpResponse) = context.proceedWith(response)
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(response: HttpResponse) -> Unit
    ) {
        val afterState = PipelinePhase("AfterState")
        client.receivePipeline.insertPhaseAfter(HttpReceivePipeline.State, afterState)
        client.receivePipeline.intercept(afterState) {
            handler(Context(this), subject)
        }
    }
}

private object SendHook : ClientHook<suspend SendHook.Context.(response: HttpRequestBuilder) -> Unit> {

    class Context(private val context: PipelineContext<Any, HttpRequestBuilder>) {
        suspend fun proceedWith(content: Any) = context.proceedWith(content)
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(request: HttpRequestBuilder) -> Unit
    ) {
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            handler(Context(this), context)
        }
    }
}

private object ReceiveHook : ClientHook<suspend ReceiveHook.Context.(call: HttpClientCall) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponseContainer, HttpClientCall>) {
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(call: HttpClientCall) -> Unit
    ) {
        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            handler(Context(this), context)
        }
    }
}
