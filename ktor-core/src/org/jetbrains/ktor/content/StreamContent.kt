package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.time.*
import java.util.concurrent.*

internal fun PipelineContext<*>.sendAsyncChannel(call: ApplicationCall, channel: AsyncReadChannel): Nothing {
    val future = createMachineCompletableFuture()

    closeAtEnd(channel, call) // TODO closeAtEnd(call) should be done globally at call start
    channel.copyToAsyncThenComplete(call.response.channel(), future, ignoreWriteError = true)
    pause()
}

internal fun PipelineContext<*>.sendStream(call: ApplicationCall, stream: InputStream): Nothing {
    val future = createMachineCompletableFuture()

    closeAtEnd(stream, call) // TODO closeAtEnd(call) should be done globally at call start
    stream.asAsyncChannel().copyToAsyncThenComplete(call.response.channel(), future, ignoreWriteError = true)
    pause()
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

internal fun PipelineContext<*>.closeAtEnd(vararg closeables: Closeable) {
    onFinish {
        for (closeable in closeables) {
            closeable.closeQuietly()
        }
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (ignore: Throwable) {
    }
}
