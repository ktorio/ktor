/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.utils.io.core.*

private const val TEST_FILE_SIZE = 1024 * 1024

internal fun Application.multiPartFormDataTest() {
    routing {
        route("/multipart") {
            post("/") {
                call.receiveMultipart().forEachPart {
                    try {
                        if (it is PartData.FileItem) {
                            val array = ByteArray(TEST_FILE_SIZE)
                            it.provider().readFully(array, 0, TEST_FILE_SIZE)
                        }
                    } finally {
                        it.dispose()
                    }
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
