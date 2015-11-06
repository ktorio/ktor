package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

fun ApplicationRequestContext.handleAsync(exec: ExecutorService, block: () -> ApplicationRequestStatus, failBlock: (Throwable) -> Unit): ApplicationRequestStatus {
    exec.submit {
        try {
            if (block() != ApplicationRequestStatus.Asynchronous) {
                close()
            }
        } catch (e: Throwable) {
            failBlock(e)
            close()
        }
    }
    return ApplicationRequestStatus.Asynchronous
}
