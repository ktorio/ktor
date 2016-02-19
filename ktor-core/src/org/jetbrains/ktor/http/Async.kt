package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.concurrent.*

fun <C: ApplicationCall> PipelineContext<C>.proceedAsync(exec: ExecutorService,
                                                         block: PipelineContext<C>.() -> Unit): Future<*> {
    pause()
    return exec.submit {
        try {
            block()
        } catch (e: Throwable) {
            execution.fail(e)
            return@submit
        }
        execution.proceed()
    }
}
