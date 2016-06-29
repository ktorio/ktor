package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

sealed class FinalContent {
    open val status: HttpStatusCode?
        get() = null

    abstract val headers: ValuesMap
    abstract fun startContent(call: ApplicationCall, context: PipelineContext<*>)

    abstract class NoContent : FinalContent() {
        override fun startContent(call: ApplicationCall, context: PipelineContext<*>) {
            call.close()
            context.finishAll()
        }
    }

    abstract class ChannelContent : FinalContent() {
        abstract fun channel(): ReadChannel

        override fun startContent(call: ApplicationCall, context: PipelineContext<*>) {
            context.sendAsyncChannel(call, channel())
        }
    }

    abstract class StreamContentProvider : FinalContent() {
        abstract fun stream(): InputStream

        override fun startContent(call: ApplicationCall, context: PipelineContext<*>) {
            context.sendStream(call, stream())
        }
    }

    abstract class StreamConsumer : FinalContent() {
        abstract fun stream(out : OutputStream): Unit

        override fun startContent(call: ApplicationCall, context: PipelineContext<*>): Nothing {
            throw UnsupportedOperationException("It should never pass here: should be resend in BaseApplicationCall instead")
        }
    }

    abstract class ProtocolUpgrade() : FinalContent() {
        abstract fun upgrade(call: ApplicationCall, context: PipelineContext<*>, input: ReadChannel, output: WriteChannel): Closeable

        override fun startContent(call: ApplicationCall, context: PipelineContext<*>): Nothing {
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
