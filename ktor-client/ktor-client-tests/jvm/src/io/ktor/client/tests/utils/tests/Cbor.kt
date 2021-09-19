/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@OptIn(ExperimentalSerializationApi::class, kotlin.ExperimentalStdlibApi::class)
fun Application.cborTest() {
    routing {
        route("cbor") {
            get("user-generic") {
                call.respondText(
                    """
                    {
                        "message": "ok",
                        "data": { "name": "hello" }
                    }
                    """.trimIndent(),
                    contentType = ContentType.Application.Cbor
                )
            }
            get("/users") {
                call.respondBytes(
                    Cbor.encodeToByteArray(listOf(User(42, "TestLogin"))),
                    contentType = ContentType.Application.Cbor
                )
            }
            get("/users-long") {
                val users = buildList { repeat(300) { add(User(it.toLong(), "TestLogin-$it")) } }
                call.respondBytes(Cbor.encodeToByteArray(users), contentType = ContentType.Application.Cbor)
            }
            get("/photos") {
                call.respondBytes(
                    Cbor.encodeToByteArray(listOf(Photo(4242, "cat.jpg"))),
                    contentType = ContentType.Application.Cbor
                )
            }
        }
    }
}
