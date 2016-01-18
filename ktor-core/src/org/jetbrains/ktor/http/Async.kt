package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

fun ApplicationCall.handleAsync(exec: ExecutorService, block: () -> ApplicationCallResult, failBlock: (Throwable) -> Unit): ApplicationCallResult {
    exec.submit {
        try {
            if (block() != ApplicationCallResult.Asynchronous) {
                close()
            }
        } catch (e: Throwable) {
            failBlock(e)
            close()
        }
    }
    return ApplicationCallResult.Asynchronous
}
