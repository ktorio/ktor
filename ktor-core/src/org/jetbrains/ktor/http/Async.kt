package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

fun ApplicationCall.handleAsync(exec: ExecutorService, block: () -> Unit, failBlock: (Throwable) -> Unit) = exec.submit {
    try {
        block()
    } catch (e: Throwable) {
        failBlock(e)
        close()
    }
}
