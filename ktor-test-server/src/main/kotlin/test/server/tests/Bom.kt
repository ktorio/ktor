/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val bom_utf_8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val bom_utf_16 = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
private val bom_utf_32 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())
internal fun Application.bomTest() {
    routing {
        route("bom") {
            get("with-bom-utf8") {
                val body = "Hello world".toByteArray()
                call.respondBytes(bom_utf_8 + body)
            }
            get("with-bom-utf16") {
                val body = "Hello world".toByteArray()
                call.respondBytes(bom_utf_16 + body)
            }
            get("with-bom-utf32") {
                val body = "Hello world".toByteArray()
                call.respondBytes(bom_utf_32 + body)
            }
            get("without-bom") {
                call.respondBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte()))
            }
            get("without-bom-short") {
                call.respondText("1")
            }
            get("without-bom-long") {
                call.respondText("Hello world")
            }
            get("empty-body") {
                call.respondText("")
            }
        }
    }
}
