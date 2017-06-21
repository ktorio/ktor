package org.jetbrains.ktor.content

import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.io.*

private val pathParameterName = "static-content-path-parameter"

val StaticRootFolderKey = AttributeKey<File>("BaseFolder")
var Route.staticRootFolder: File?
    get() = attributes.getOrNull(StaticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        value?.let { attributes.put(StaticRootFolderKey, it) }
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

fun Route.static(configure: Route.() -> Unit): Route {
    // need to create new Route to isolate its attributes
    val route = Route(this, UnconditionalRouteSelector).apply(configure)
    children.add(route)
    return route
}

fun Route.static(remotePath: String, configure: Route.() -> Unit) = route(remotePath, configure)

fun Route.default(localPath: String) = default(File(localPath))
fun Route.default(localPath: File) {
    get {
        val file = staticRootFolder.combine(localPath)
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

fun Route.file(remotePath: String, localPath: String = remotePath) = file(remotePath, File(localPath))
fun Route.file(remotePath: String, localPath: File) {
    get(remotePath) {
        val file = staticRootFolder.combine(localPath)
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

fun Route.files(folder: String) = files(File(folder))
fun Route.files(folder: File) {
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = staticRootFolder.combine(folder).combineSafe(relativePath)
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

val BasePackageKey = AttributeKey<String>("BasePackage")
var Route.staticBasePackage: String?
    get() = attributes.getOrNull(BasePackageKey)
    set(value) {
        value?.let { attributes.put(BasePackageKey, it) }
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

fun Route.resource(remotePath: String, relativePath: String = remotePath, resourcePackage: String? = null) {
    get(remotePath) {
        val content = call.resolveResource(relativePath, staticBasePackage.combinePackage(resourcePackage))
        if (content != null)
            call.respond(content)
    }
}

fun Route.resources(resourcePackage: String? = null) {
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val content = call.resolveResource(relativePath, staticBasePackage.combinePackage(resourcePackage))
        if (content != null)
            call.respond(content)
    }
}

fun Route.defaultResource(relativePath: String, resourcePackage: String? = null) {
    get {
        val content = call.resolveResource(relativePath, staticBasePackage.combinePackage(resourcePackage))
        if (content != null)
            call.respond(content)
    }
}
