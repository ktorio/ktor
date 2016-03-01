package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.concurrent.*

fun <C: ApplicationCall> PipelineContext<C>.proceedAsync(exec: ExecutorService,
                                                         block: PipelineContext<C>.() -> Unit): Future<*> {
    pipeline.pause()
    return exec.submit {
        try {
            block()
        } catch (e: Throwable) {
            pipeline.fail(e)
            return@submit
        }
        pipeline.proceed()
    }
}
