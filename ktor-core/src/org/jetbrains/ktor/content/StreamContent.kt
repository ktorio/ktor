package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.time.*
import java.util.concurrent.*

interface Resource : HasVersions {
    val contentType: ContentType
    override val versions: List<Version>
    val expires: LocalDateTime?
    val cacheControl: CacheControl?
    @Deprecated("Shouldn't it be somewhere else instead?")
    val attributes: Attributes
    val contentLength: Long?

    override val headers: ValuesMap
        get() = ValuesMap.build(true) {
            appendAll(super.headers)
            contentType(contentType)
            expires?.let { expires ->
                expires(expires)
            }
            cacheControl?.let { cacheControl ->
                cacheControl(cacheControl)
            }
            contentLength?.let { contentLength ->
                contentLength(contentLength)
            }
        }
}

sealed class FinalContent {
    open val status: HttpStatusCode?
        get() = null

    abstract val headers: ValuesMap
    abstract fun startContent(call: ApplicationCall, context: PipelineContext<Any>)

    abstract class NoContent : FinalContent() {
        override fun startContent(call: ApplicationCall, context: PipelineContext<Any>) {
            call.close()
            context.finishAll()
        }
    }

    abstract class ChannelContent : FinalContent() {
        abstract fun channel(): AsyncReadChannel

        override fun startContent(call: ApplicationCall, context: PipelineContext<Any>) {
            context.sendAsyncChannel(call, channel())
        }
    }

    abstract class StreamContentProvider : FinalContent() {
        abstract fun stream(): InputStream

        override fun startContent(call: ApplicationCall, context: PipelineContext<Any>) {
            context.sendStream(call, stream())
        }
    }

    abstract class StreamConsumer : FinalContent() {
        abstract fun stream(out : OutputStream): Unit

        override fun startContent(call: ApplicationCall, context: PipelineContext<Any>): Nothing {
            throw UnsupportedOperationException("It should never pass here: should be resend in BaseApplicationCall instead")
        }
    }

    abstract class ProtocolUpgrade() : FinalContent() {
        abstract fun upgrade(call: ApplicationCall, context: PipelineContext<Any>, input: AsyncReadChannel, output: AsyncWriteChannel): Closeable

        override fun startContent(call: ApplicationCall, context: PipelineContext<Any>): Nothing {
            throw UnsupportedOperationException("It should never pass here: should be container-specific and handled in contained-specific ApplicationCall implementation")
        }
    }

}

fun FinalContent.contentLength(): Long? {
    if (this is Resource) {
        return contentLength
    }

    return headers[HttpHeaders.ContentLength]?.let { it.toLong() }
}

fun FinalContent.contentType(): ContentType? {
    if (this is Resource) {
        return contentType
    }

    return headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
}

fun FinalContent.lastModifiedAndEtagVersions(): List<Version> {
    if (this is HasVersions) {
        return versions
    }

    val headers = headers
    return headers.getAll(HttpHeaders.LastModified).orEmpty().map { LastModifiedVersion(LocalDateTime.parse(it, httpDateFormat)) } +
            headers.getAll(HttpHeaders.ETag).orEmpty().map { EntityTagVersion(it) }
}

private fun PipelineContext<*>.createMachineCompletableFuture() = CompletableFuture<Long>().apply {
    whenComplete { total, throwable ->
        runBlockWithResult {
            handleThrowable(throwable)
        }
    }
}

private fun PipelineContext<*>.handleThrowable(throwable: Throwable?) {
    if (throwable == null || throwable is PipelineContinue || throwable.cause is PipelineContinue) {
        finishAll()
    } else if (throwable !is PipelineControlFlow && throwable.cause !is PipelineControlFlow) {
        fail(throwable)
    }
}

private fun PipelineContext<Any>.sendAsyncChannel(call: ApplicationCall, channel: AsyncReadChannel): Nothing {
    val future = createMachineCompletableFuture()

    closeAtEnd(channel, call) // TODO closeAtEnd(call) should be done globally at call start
    channel.copyToAsyncThenComplete(call.response.channel(), future, ignoreWriteError = true)
    pause()
}

private fun PipelineContext<Any>.sendStream(call: ApplicationCall, stream: InputStream): Nothing {
    val future = createMachineCompletableFuture()

    closeAtEnd(stream, call) // TODO closeAtEnd(call) should be done globally at call start
    stream.asAsyncChannel().copyToAsyncThenComplete(call.response.channel(), future, ignoreWriteError = true)
    pause()
}


internal fun PipelineContext<*>.closeAtEnd(vararg closeables: Closeable) {
    fun end() {
        for (closeable in closeables) {
            closeable.closeQuietly()
        }
    }

    onSuccess {
        end()
    }
    onFail {
        end()
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (ignore: Throwable) {
    }
}
