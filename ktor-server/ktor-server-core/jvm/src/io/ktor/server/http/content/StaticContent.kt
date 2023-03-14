/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.io.*
import java.net.URL

private const val pathParameterName = "static-content-path-parameter"

private val staticRootFolderKey = AttributeKey<File>("BaseFolder")

private val StaticContentAutoHead = createRouteScopedPlugin("StaticContentAutoHead") {

    class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
        override val status: HttpStatusCode? get() = original.status
        override val contentType: ContentType? get() = original.contentType
        override val contentLength: Long? get() = original.contentLength
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
        override val headers get() = original.headers
    }

    on(ResponseBodyReadyForSend) { call, content ->
        check(call.request.local.method == HttpMethod.Head)
        transformBodyTo(HeadResponse(content))
    }
}

/**
 * A config for serving static content
 */
public class StaticContentConfig<Resource : Any> internal constructor() {

    internal var contentType: (Resource) -> ContentType = {
        when (it) {
            is File -> ContentType.defaultForFile(it)
            is URL -> ContentType.defaultForFilePath(it.path)
            else -> throw IllegalArgumentException("Argument can be only of type File or URL, but was ${it::class}")
        }
    }
    internal var cacheControl: (Resource) -> List<CacheControl> = { emptyList() }
    internal var extensions: List<String> = emptyList()

    /**
     * Support pre-compressed files or resources.
     *
     * For example, for static files, by setting `preCompressed = listOf(CompressedFileType.BROTLI)`, the local file
     * /foo/bar.js.br can be found at "/foo/bar.js"
     *
     * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found.
     *
     * The order in types is *important*.
     * It will determine the priority of serving one versus serving another.
     */
    public var preCompressedFileTypes: List<CompressedFileType> = emptyList()

    /**
     * If set to true, automatically responds to a `HEAD` request for every file/resource that has a `GET` defined.
     */
    public var autoHeadResponse: Boolean = false

    /**
     * Configures [ContentType] for requested static content.
     * For files, [Resource] is a request [File].
     * For resources, [Resource] is a path to requested resource.
     */
    public fun contentType(block: (Resource) -> ContentType) {
        contentType = block
    }

    /**
     * Configures [CacheControl] for requested static content.
     * For files, [Resource] is a request [File].
     * For resources, [Resource] is a path to requested resource.
     */
    public fun cacheControl(block: (Resource) -> List<CacheControl>) {
        cacheControl = block
    }

    /**
     * Configures file extension fallbacks.
     * When set, if a file is not found, the search will repeat with the given extensions added to the file name.
     * The first match will be served.
     */
    public fun extensions(vararg extensions: String) {
        this.extensions = extensions.toList()
    }
}

/**
 * Sets up [Routing] to serve static files.
 * All files inside [dir] will be accessible recursively at "[remotePath]/path/to/file".
 * If requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If requested file doesn't exist, or it is a directory and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticFiles(
    remotePath: String,
    dir: File,
    index: String? = "index.html",
    block: StaticContentConfig<File>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<File>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    return route(remotePath) {
        route("{$pathParameterName...}") {
            suspend fun handler(call: ApplicationCall) {
                val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return
                val requestedFile = dir.combineSafe(relativePath)
                val file = if (index != null && requestedFile.isDirectory) {
                    File(dir.combineSafe(relativePath), index)
                } else {
                    requestedFile
                }
                call.respondStaticFile(file, compressedTypes, contentType, cacheControl)
                if (call.isHandled || file != requestedFile) return
                for (extension in extensions) {
                    val fileWithExtension = File("${file.path}.$extension")
                    call.respondStaticFile(fileWithExtension, compressedTypes, contentType, cacheControl)
                    if (call.isHandled) return
                }
            }

            get {
                handler(call)
            }
            if (autoHead) {
                method(HttpMethod.Head) {
                    install(StaticContentAutoHead)
                    handle { handler(call) }
                }
            }
        }
    }
}

/**
 * Sets up [Routing] to serve resources as static content.
 * All resources inside [basePackage] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested resource doesn't exist and [index] is not `null`,
 * then response will be [index] resource in the requested package.
 *
 * If requested resource doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticResources(
    remotePath: String,
    basePackage: String?,
    index: String? = "index.html",
    block: StaticContentConfig<URL>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<URL>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    return route(remotePath) {
        route("{$pathParameterName...}") {
            suspend fun handler(call: ApplicationCall) {
                val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return
                call.respondStaticResource(
                    requestedResource = relativePath,
                    packageName = basePackage,
                    compressedTypes = compressedTypes,
                    contentType = contentType,
                    cacheControl = cacheControl
                )
                if (call.isHandled || index == null) return
                call.respondStaticResource(
                    requestedResource = "$relativePath${File.separator}$index",
                    packageName = basePackage,
                    compressedTypes = compressedTypes,
                    contentType = contentType,
                    cacheControl = cacheControl
                )
                if (call.isHandled) return
                for (extension in extensions) {
                    call.respondStaticResource(
                        requestedResource = "$relativePath.$extension",
                        packageName = basePackage,
                        compressedTypes = compressedTypes,
                        contentType = contentType,
                        cacheControl = cacheControl
                    )
                    if (call.isHandled) return
                }
            }

            get {
                handler(call)
            }
            if (autoHead) {
                method(HttpMethod.Head) {
                    install(StaticContentAutoHead)
                    handle { handler(call) }
                }
            }
        }
    }
}

/**
 * Support pre-compressed files and resources
 *
 * For example, by using [preCompressed()] (or [preCompressed(CompressedFileType.BROTLI)]), the local file
 * /foo/bar.js.br can be found @ http..../foo/bar.js
 *
 * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found
 *
 * Notes:
 *
 * * The order in types is *important*. It will determine the priority of serving one versus serving another
 *
 * * This can't be disabled in a child route if it was enabled in the root route
 */
public fun Route.preCompressed(
    vararg types: CompressedFileType = CompressedFileType.values(),
    configure: Route.() -> Unit
) {
    val existing = staticContentEncodedTypes ?: emptyList()
    val mixedTypes = (existing + types.asList()).distinct()
    attributes.put(compressedKey, mixedTypes)
    configure()
    attributes.remove(compressedKey)
}

/**
 * Base folder for relative files calculations for static content
 */
public var Route.staticRootFolder: File?
    get() = attributes.getOrNull(staticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        if (value != null) {
            attributes.put(staticRootFolderKey, value)
        } else {
            attributes.remove(staticRootFolderKey)
        }
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

/**
 * Create a block for static content
 */
public fun Route.static(configure: Route.() -> Unit): Route = apply(configure)

/**
 * Create a block for static content at specified [remotePath]
 */
public fun Route.static(remotePath: String, configure: Route.() -> Unit): Route = route(remotePath, configure)

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
public fun Route.default(localPath: String): Unit = default(File(localPath))

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
public fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
public fun Route.file(remotePath: String, localPath: String = remotePath): Unit = file(remotePath, File(localPath))

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
public fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get(remotePath) {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve all files from [folder]
 */
public fun Route.files(folder: String): Unit = files(File(folder))

/**
 * Sets up routing to serve all files from [folder].
 * Serves [defaultFile] if a missing file is requested.
 * Serves [defaultFile] if the requested file should be ignored by [shouldFileBeIgnored].
 */
internal fun Route.filesWithDefault(
    folder: String,
    defaultFile: String,
    shouldFileBeIgnored: (String) -> Boolean
): Unit =
    filesWithDefaultFile(File(folder), File(defaultFile), shouldFileBeIgnored)

/**
 * Sets up routing to serve all files from [folder]
 */
public fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve all files from [folder].
 * Serves [defaultFile] if a missing file is requested.
 * Serves [defaultFile] if the requested file should be ignored by [shouldFileBeIgnored].
 */
internal fun Route.filesWithDefaultFile(
    folder: File,
    defaultFile: File,
    shouldFileBeIgnored: (String) -> Boolean
) {
    val dir = staticRootFolder.combine(folder)

    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get

        if (shouldFileBeIgnored.invoke(relativePath)) {
            call.respondStaticFile(dir.combine(defaultFile), compressedTypes)
            return@get
        }

        val file = dir.combineSafe(relativePath)
        call.respondStaticFile(file, compressedTypes)

        if (!call.isHandled) {
            call.respondStaticFile(dir.combine(defaultFile), compressedTypes)
        }
    }
}

private val staticBasePackageName = AttributeKey<String>("BasePackage")

/**
 * Base package for relative resources calculations for static content
 */
public var Route.staticBasePackage: String?
    get() = attributes.getOrNull(staticBasePackageName) ?: parent?.staticBasePackage
    set(value) {
        if (value != null) {
            attributes.put(staticBasePackageName, value)
        } else {
            attributes.remove(staticBasePackageName)
        }
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

/**
 * Sets up routing to serve [resource] as [remotePath] in [resourcePackage]
 */
public fun Route.resource(remotePath: String, resource: String = remotePath, resourcePackage: String? = null) {
    val compressedTypes = staticContentEncodedTypes
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get(remotePath) {
        call.respondStaticResource(
            requestedResource = resource,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 * Sets up routing to serve all resources in [resourcePackage].
 * Serves [defaultFile] if a missing file is requested.
 * Serves [defaultFile] if the requested file should be ignored by [shouldFileBeIgnored].
 */
internal fun Route.resourceWithDefault(
    resourcePackage: String? = null,
    defaultResource: String,
    shouldFileBeIgnored: (String) -> Boolean
) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get

        if (shouldFileBeIgnored.invoke(relativePath)) {
            call.respondStaticResource(
                requestedResource = defaultResource,
                packageName = packageName,
                compressedTypes = compressedTypes
            )
            return@get
        }

        call.respondStaticResource(
            requestedResource = relativePath,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
        if (!call.isHandled) {
            call.respondStaticResource(
                requestedResource = defaultResource,
                packageName = packageName,
                compressedTypes = compressedTypes
            )
        }
    }
}

/**
 * Sets up routing to serve all resources in [resourcePackage]
 */
public fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        call.respondStaticResource(
            requestedResource = relativePath,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 * Specifies [resource] as a default resources to serve when folder is requested
 */
public fun Route.defaultResource(resource: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes
    get {
        call.respondStaticResource(
            requestedResource = resource,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 *  Checks if the application call is requesting static content
 */
public fun ApplicationCall.isStaticContent(): Boolean = parameters.contains(pathParameterName)
