package io.ktor.samples.http2

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.*

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}

class CertificateGenerator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val file = File("build/temporary.jks")

            if (!file.exists()) {
                file.parentFile.mkdirs()
                generateCertificate(file)
            }
        }
    }
}