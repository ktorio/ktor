package io.ktor.samples.staticcontent

import io.ktor.content.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.*


fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        routing {
            static("static") {
                // If running using IntelliJ IDEA use the corresponding Run Configuration so that it sets the working directory correctly
                files("css")
                files("js")
                file("image.png")
                file("random.txt", "image.png")
                default("index.html")
            }
            static("custom") {
                staticRootFolder = File("/tmp") // Establishes a root folder
                files("public") // For this to work, make sure you have /tmp/public on your system
                static("themes") {
                    // services /custom/themes
                    files("data")
                }
            }
        }
    }.start()
}
