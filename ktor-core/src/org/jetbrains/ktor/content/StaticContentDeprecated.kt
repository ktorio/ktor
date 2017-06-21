package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import java.io.*
import java.nio.file.*


@Suppress("DEPRECATION")
@Deprecated("Use 'static { }' builder instead")
fun Route.serveFileSystem(baseDir: Path) = serveFileSystem(baseDir.toFile())

@Deprecated("Use 'static { }' builder instead")
fun Route.serveFileSystem(folder: File) {
    static {
        files(folder)
    }
}

@Deprecated("Use 'static { }' builder instead")
fun Route.serveClasspathResources(basePackage: String = "") {
    route("{path...}") {
        handle {
            call.resolveResource(call.parameters.getAll("path")!!.joinToString(File.separator), basePackage)?.let {
                call.respond(it)
            }
        }
    }
}

/**
 * @param contextPath is a path prefix to be cut from the request path
 * @param basePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [ResourceFileContent] or `null`
 */
@Deprecated("Use 'static { }' builder instead")
fun ApplicationCall.resolveClasspathResource(contextPath: String, basePackage: String, mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }): Resource? {
    val path = request.path()
    if (!path.startsWith(contextPath)) {
        return null
    }

    val sub = path.removePrefix(contextPath)
    if (!sub.startsWith('/')) {
        return null
    }

    return resolveResource(sub, basePackage, mimeResolve = mimeResolve)
}

/**
 * @param contextPath is a path prefix to be cut from the request path
 * @param baseDir is a base directory the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or `null` if the file is out of the [baseDir] or doesn't exist
 */
@Deprecated("Use 'static { }' builder instead")
fun ApplicationCall.resolveLocalFile(contextPath: String, baseDir: File, mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }): LocalFileContent? {
    val path = request.path()
    if (!path.startsWith(contextPath)) {
        return null
    }

    val sub = path.removePrefix(contextPath)
    if (!sub.startsWith('/')) {
        return null
    }

    val relativePath = sub.removePrefix("/").replace("/", File.separator)
    val canonicalBase = baseDir.canonicalFile
    val destination = File(canonicalBase, relativePath).canonicalFile

    if (!destination.startsWith(canonicalBase)) {
        return null
    }

    if (!destination.exists()) {
        return null
    }

    return LocalFileContent(destination, mimeResolve(destination.extension))
}
