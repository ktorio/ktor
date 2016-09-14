package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.io.*
import java.nio.file.*

fun Route.serveClasspathResources(basePackage: String = "") {
    route("{path...}") {
        handle {
            call.resolveClasspathWithPath(basePackage, call.parameters.getAll("path")!!.joinToString(File.separator))?.let {
                call.respond(it)
            }
        }
    }
}

fun Route.serveFileSystem(baseDir: Path) = serveFileSystem(baseDir.toFile())

fun Route.serveFileSystem(baseDir: File) {
    route("{path...}") {
        handle {
            val message = LocalFileContent(baseDir, call.parameters.getAll("path")!!.joinToString(File.separator))
            if (!message.file.isFile) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(message)
            }
        }
    }
}

internal fun defaultContentType(extension: String) = ContentTypeByExtension.lookupByExtension(extension).firstOrNull() ?: ContentType.Application.OctetStream
