/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.sse.SSE")

/**
 * Server-Sent Events (SSE) support plugin. It is required to be installed first before binding any sse endpoints.
 *
 * To learn more, see [specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse("/default") {
 *         repeat(100) {
 *             send(ServerSentEvent("event $it"))
 *         }
 *     }
 *
 *     sse("/serialization", serialize = { typeInfo, it ->
 *         val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *         Json.encodeToString(serializer, it)
 *     }) {
 *         send(Customer(0, "Jet", "Brains"))
 *         send(Product(0, listOf(100, 200)))
 *     }
 * }
 * ```
 */
public val SSE: ApplicationPlugin<Unit> = createApplicationPlugin("SSE") {}
