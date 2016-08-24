package org.jetbrains.ktor.host

fun defaultHostPipeline() = HostPipeline().apply {
    intercept(HostPipeline.Before, {
        onFinish {
            subject.close()
        }
    })
    intercept(HostPipeline.Call) {
        fork(subject, subject.application)
    }

    setupDefaultHostPages(HostPipeline.Before, HostPipeline.Fallback)
}

