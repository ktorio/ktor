/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

fun Application.installNullableValueClasses() {
    install(ContentNegotiation) {
        jsonIo()
    }

    routing {
        post("/nullable-value-classes") {
            val request = call.receive<NullableValueClassRequest>()
            call.respond(
                NullableValueClassResponse(
                    nullableText = request.nullableText,
                    token = request.token,
                    optionalToken = request.optionalToken,
                    tokenWithNullableContent = request.tokenWithNullableContent,
                    optionalCount = request.optionalCount,
                )
            )
        }
    }
}

@JvmInline
@Serializable
value class SessionToken(val value: String)

@JvmInline
@Serializable
value class AttemptCount(val value: Int)

@JvmInline
@Serializable
value class SessionTokenWithNullableValue(val value: String?)

@Serializable
data class NullableValueClassRequest(
    val nullableText: String?,
    val token: SessionToken,
    val optionalToken: SessionToken?,
    val tokenWithNullableContent: SessionTokenWithNullableValue,
    val optionalCount: AttemptCount?,
)

@Serializable
data class NullableValueClassResponse(
    val nullableText: String?,
    val token: SessionToken,
    val optionalToken: SessionToken?,
    val tokenWithNullableContent: SessionTokenWithNullableValue,
    val optionalCount: AttemptCount?,
)
