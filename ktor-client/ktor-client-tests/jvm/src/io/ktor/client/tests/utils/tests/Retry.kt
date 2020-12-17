/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests


import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.collections.*
import java.util.concurrent.atomic.*

public fun Application.retryTests() {
    val sessions = ConcurrentMap<String, AtomicInteger>()

    routing {
        route("retry") {
            get("status") {
                val id = call.request.queryParameters["id"]!!
                val counter = sessions.computeIfAbsent(id) { AtomicInteger() }
                if (counter.incrementAndGet() >= 3) {
                    sessions.remove(id)
                    call.respondText("OK, $id")
                } else {
                    call.respondText("Failure for $id", status = HttpStatusCode.NotFound)
                }
            }
        }
    }
}
