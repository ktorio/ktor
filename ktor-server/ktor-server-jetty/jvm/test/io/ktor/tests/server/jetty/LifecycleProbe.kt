/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.server.application.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records Ktor application lifecycle events observed from within an application module.
 *
 * The probe is a JVM-wide singleton so that the events recorded by the module loaded inside the
 * servlet container can be asserted from the test that started the container.
 */
internal object LifecycleProbe {
    val events: MutableList<String> = CopyOnWriteArrayList()

    fun reset() {
        events.clear()
    }
}

/**
 * Application module referenced from `application-lifecycle.conf`.
 *
 * It mirrors what a user does in their own application: subscribe to lifecycle events via the
 * application monitor. This is the only realistic way to observe these events for a servlet that
 * bootstraps itself from configuration (the WAR deployment scenario).
 */
fun Application.lifecycleProbeModule() {
    monitor.subscribe(ApplicationStarted) { LifecycleProbe.events += "started" }
    monitor.subscribe(ApplicationStopping) { LifecycleProbe.events += "stopping" }
    monitor.subscribe(ApplicationStopped) { LifecycleProbe.events += "stopped" }
}
