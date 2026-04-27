/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.FileSystemPaths.Companion.paths
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.nio.file.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/**
 * Attribute to assign the path of a static file served in the response.  The main use of this attribute is to indicate
 * to subsequent interceptors that a static file was served via the `ApplicationCall.isStaticContent()` extension
 * function.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticFileLocationProperty)
 */
public val StaticFileLocationProperty: AttributeKey<String> = AttributeKey("StaticFileLocation")

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
        if (content is OutgoingContent.ReadChannelContent) content.readFrom().cancel(null)
        transformBodyTo(HeadResponse(content))
    }
}

/**
 * A config for serving static content
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig)
 */
public class StaticContentConfig<Resource : Any> internal constructor() {

    private val defaultContentType: (Resource) -> ContentType = {
        when (it) {
            is File -> ContentType.defaultForFile(it)
            is URL -> ContentType.defaultForFilePath(it.path)
            is Path -> ContentType.defaultForPath(it)
            else ->
                throw IllegalArgumentException("Argument can be only of type File, Path or URL, but was ${it::class}")
        }
    }
    internal var contentType: (Resource) -> ContentType = defaultContentType
    internal var cacheControl: (Resource) -> List<CacheControl> = { emptyList() }
    internal var modifier: suspend (Resource, ApplicationCall) -> Unit = { _, _ -> }
    internal var exclude: MutableList<(Resource) -> Boolean> = mutableListOf()
    internal var filter: MutableList<(call: ApplicationCall) -> Boolean> = mutableListOf()
    internal var extensions: Array<String> = emptyArray()
    internal var defaultPath: String? = null
    internal var fallback: suspend (String, ApplicationCall) -> Unit = { _, _ -> }
    internal var preCompressedFileTypes: Array<CompressedFileType> = emptyArray()
    internal var autoHeadResponse: Boolean = false
    internal var lastModifiedExtractor: (Resource) -> GMTDate? = { null }
    internal var etagExtractor: ETagProvider = ETagProvider { null }

    /**
     * Enables pre-compressed files or resources.
     *
     * For example, for static files, by setting `preCompressed(CompressedFileType.BROTLI)`, the local file
     * /foo/bar.js.br can be found at "/foo/bar.js"
     *
     * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found.
     *
     * The order in types is *important*.
     * It will determine the priority of serving one versus serving another.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.preCompressed)
     */
    public fun preCompressed(vararg types: CompressedFileType) {
        preCompressedFileTypes = types.toList().toTypedArray() // workaround for annoying cast warnings
    }

    /**
     * Enables automatic response to a `HEAD` request for every file/resource that has a `GET` defined.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.enableAutoHeadResponse)
     */
    public fun enableAutoHeadResponse() {
        autoHeadResponse = true
    }

    /**
     * Configures default [Resource] to respond with, when requested file is not found.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.default)
     */
    public fun default(path: String?) {
        this.defaultPath = path
    }

    /**
     * Configures custom fallback behavior when a requested static resource is not found.
     *
     * This function allows you to provide a callback that inspects the originally requested path (e.g. "plugins/file.php")
     * and the [ApplicationCall], and then perform custom logic such as:
     * - redirecting to a different path,
     * - responding with a specific HTTP status (e.g. 410 Gone or 400 Bad Request),
     * - or serving an alternative static file manually.
     *
     * Example:
     * ```
     * staticFiles("/static", File("files")) {
     *   fallback { requestedPath, call ->
     *     when {
     *       requestedPath.endsWith(".php") -> call.respondRedirect("/static/index.html")
     *       requestedPath.endsWith(".xml") -> call.respond(HttpStatusCode.Gone)
     *       else -> call.respondFile(File("files/index.html"))
     *     }
     *   }
     * }
     * ```
     *
     * This differs from the existing `default(path: String?)`:
     * - `default(...)` serves a fixed, path‑agnostic fallback resource.
     * - `fallback { requestedPath, call -> ... }` gives full control and context for conditional behavior.
     * @see default
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.fallback)
     */
    public fun fallback(fallback: suspend (String, ApplicationCall) -> Unit) {
        this.fallback = fallback
    }

    /**
     * Configures [ContentType] for requested static content.
     * If the [block] returns `null`, default behaviour of guessing [ContentType] from the header will be used.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.contentType)
     */
    public fun contentType(block: (Resource) -> ContentType?) {
        contentType = { resource -> block(resource) ?: defaultContentType(resource) }
    }

    /**
     * Configures [CacheControl] for requested static content.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.cacheControl)
     */
    public fun cacheControl(block: (Resource) -> List<CacheControl>) {
        cacheControl = block
    }

    /**
     * Configures modification of a call for requested content.
     * Useful to add headers to the response, such as [HttpHeaders.ETag]
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.modify)
     */
    public fun modify(block: suspend (Resource, ApplicationCall) -> Unit) {
        modifier = block
    }

    /**
     * Configures [HttpHeaders.LastModified] for requested static content.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * Note: for this functionality to work, you need to install the [ConditionalHeaders] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.lastModified)
     */
    public fun lastModified(block: (Resource) -> GMTDate?) {
        lastModifiedExtractor = block
    }

    /**
     * Configures [HttpHeaders.ETag] for requested content.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * Note: for this functionality to work, you need to install the [ConditionalHeaders] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.etag)
     */
    public fun etag(block: ETagProvider) {
        etagExtractor = block
    }

    /**
     * Configures resources that should not be served.
     *
     * If this block returns `true` for [Resource], the [Application] will
     * respond with [HttpStatusCode.Forbidden].
     * Can be invoked multiple times.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.exclude)
     */
    public fun exclude(block: (Resource) -> Boolean) {
        exclude.add(block)
    }

    /**
     * Configures calls that should be skipped.
     *
     * If this block returns `true` for [ApplicationCall], the [Application]
     * will not handle any static content.
     *
     * Useful if, for example, you are serving static content at the root
     * domain, but don't want to serve static content for any requests to the
     * `/api` route.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.exclude)
     */
    public fun filter(block: (call: ApplicationCall) -> Boolean) {
        filter.add(block)
    }

    /**
     * Configures file extension fallbacks.
     * When set, if a file is not found, the search will repeat with the given extensions added to the file name.
     * The first match will be served.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.StaticContentConfig.extensions)
     */
    public fun extensions(vararg extensions: String) {
        this.extensions = extensions.toList().toTypedArray()
    }
}

/**
 * Used for pre-compressed static files which are stored in memory.
 */
internal data class CachedStaticFile(
    val path: Path,
    val bytes: ByteArray,
    val cacheControl: List<CacheControl>,
    val contentType: ContentType,
    val etag: EntityTagVersion?,
    val lastModified: GMTDate?,
) {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is CachedStaticFile -> false
        path != other.path -> false
        !bytes.contentEquals(other.bytes) -> false
        cacheControl != other.cacheControl -> false
        contentType != other.contentType -> false
        etag != other.etag -> false
        lastModified != other.lastModified -> false
        else -> true
    }

    override fun hashCode() = Objects.hash(path, bytes, cacheControl, contentType, etag, lastModified)
}

/**
 * Sets up [RoutingRoot] to serve static files.
 * All files inside [dir] will be accessible recursively at "[remotePath]/path/to/file".
 * If the requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If the requested file doesn't exist, or it is a directory and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticFiles)
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
    val modify = staticRoute.modifier
    val exclude = staticRoute.exclude.flattenExcludeFunctions()
    val filter = staticRoute.filter.flattenExcludeFunctions()
    val defaultPath = staticRoute.defaultPath
    val fallback = staticRoute.fallback
    val lastModified = staticRoute.lastModifiedExtractor
    val etag = staticRoute.etagExtractor

    return staticContentRoute(remotePath, autoHead) {
        if (filter(this)) return@staticContentRoute

        val relativePath = relativePath() ?: return@staticContentRoute

        respondStaticFile(
            relativePath = relativePath,
            index = index,
            dir = dir,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            lastModified = lastModified,
            etag = etag,
            modify = modify,
            exclude = exclude,
            extensions = extensions,
        )

        if (isHandled) return@staticContentRoute
        if (defaultPath != null) {
            respondStaticFile(
                File(dir, defaultPath),
                compressedTypes,
                contentType,
                cacheControl,
                lastModified,
                etag,
                modify
            )
        }

        if (isHandled) return@staticContentRoute
        fallback(relativePath, this)
    }
}

/**
 * Sets up [RoutingRoot] to serve resources as static content.
 * All resources inside [basePackage] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested resource doesn't exist and [index] is not `null`,
 * then response will be [index] resource in the requested package.
 *
 * If requested resource doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticResources)
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
    val modifier = staticRoute.modifier
    val exclude = staticRoute.exclude.flattenExcludeFunctions()
    val filter = staticRoute.filter.flattenExcludeFunctions()
    val defaultPath = staticRoute.defaultPath
    val fallback = staticRoute.fallback
    val lastModified = staticRoute.lastModifiedExtractor
    val etag = staticRoute.etagExtractor

    return staticContentRoute(remotePath, autoHead) {
        if (filter(this)) return@staticContentRoute

        val relativePath = relativePath() ?: return@staticContentRoute

        respondStaticResource(
            relativePath = relativePath,
            index = index,
            basePackage = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            lastModified = lastModified,
            etag = etag,
            modifier = modifier,
            exclude = exclude,
            extensions = extensions,
        )

        if (isHandled) return@staticContentRoute
        if (defaultPath != null) {
            respondStaticResource(
                requestedResource = defaultPath,
                packageName = basePackage,
                compressedTypes = compressedTypes,
                contentType = contentType,
                cacheControl = cacheControl,
                modifier = modifier,
                lastModified = lastModified,
                etag = etag,
            )
        }

        if (isHandled) return@staticContentRoute
        fallback(relativePath, this)
    }
}

/**
 * Sets up [RoutingRoot] to serve contents of [zip] as static content.
 * All paths inside [basePath] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested path doesn't exist and [index] is not `null`,
 * then response will be [index] path in the requested package.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticZip)
 */
public fun Route.staticZip(
    remotePath: String,
    basePath: String?,
    zip: Path,
    index: String? = "index.html",
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route = staticFileSystem(
    remotePath = remotePath,
    basePath = basePath,
    index = index,
    fileSystem = ReloadingZipFileSystem(
        zip,
        environment.classLoader,
        getFileSystem(zip, environment.classLoader).paths()
    ),
    block = block
)

private fun getFileSystem(zip: Path, classLoader: ClassLoader): FileSystem = FileSystems.newFileSystem(zip, classLoader)

/**
 * Allow to serve changing [FileSystem]. Returns [FileSystemPaths],
 * which will be recreated on each request if there were any file changes.
 */
private class ReloadingZipFileSystem(
    private val zip: Path,
    private val classLoader: ClassLoader,
    private var delegate: FileSystemPaths
) : FileSystemPaths {
    private val watchService = FileSystems.getDefault().newWatchService()

    init {
        zip.parent.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.OVERFLOW
        )
    }

    override fun getPath(first: String, vararg more: String): Path {
        val key = watchService.poll() ?: return delegate.getPath(first, *more)

        val events = key.pollEvents()
        if (events.isNotEmpty()) {
            delegate = getFileSystem(zip, classLoader).paths()
        }
        key.reset()

        return delegate.getPath(first, *more)
    }
}

/**
 * Sets up [RoutingRoot] to serve [fileSystem] as static content.
 * All paths inside [dir] will be accessible recursively at "[remotePath]/path/to/resource".
 * If the requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticFileSystem)
 */
public fun Route.staticPaths(
    remotePath: String,
    dir: Path?,
    index: Path? = Path("index.html"),
    fileSystem: FileSystemPaths = FileSystems.getDefault().paths(),
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route {
    return staticFileSystem(remotePath, dir, index, fileSystem, block)
}

/**
 * Sets up [RoutingRoot] to serve [fileSystem] as static content.
 * All paths inside [basePath] will be accessible recursively at "[remotePath]/path/to/resource".
 * If the requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticFileSystem)
 */
public fun Route.staticFileSystem(
    remotePath: String,
    basePath: String?,
    index: String? = "index.html",
    fileSystem: FileSystemPaths = FileSystems.getDefault().paths(),
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route {
    return staticFileSystem(
        remotePath,
        if (basePath != null) fileSystem.getPath(basePath) else null,
        if (index != null) fileSystem.getPath(index) else null,
        fileSystem,
        block
    )
}

/**
 * Sets up [RoutingRoot] to serve [fileSystem] as static content.
 * All paths inside [dir] will be accessible recursively at "[remotePath]/path/to/resource".
 * If the requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticFileSystem)
 */
public fun Route.staticFileSystem(
    remotePath: String,
    dir: Path?,
    index: Path? = Path("index.html"),
    fileSystem: FileSystemPaths = (dir?.fileSystem ?: FileSystems.getDefault()).paths(),
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<Path>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    val modify = staticRoute.modifier
    val exclude = staticRoute.exclude.flattenExcludeFunctions()
    val filter = staticRoute.filter.flattenExcludeFunctions()
    val defaultPathString = staticRoute.defaultPath
    val fallback = staticRoute.fallback
    val lastModified = staticRoute.lastModifiedExtractor
    val etag = staticRoute.etagExtractor

    val defaultPath = defaultPathString?.let {
        dir?.resolve(defaultPathString) ?: fileSystem.getPath(defaultPathString)
    }
    var defaultFile: CachedStaticFile? = null
    var defaultCompressedFiles: Array<Pair<CachedStaticFile, CompressedFileType>>? = null

    if (defaultPath != null && defaultPath.exists() && defaultPath.isRegularFile()) {
        fun updateDefaultFile() {
            if (defaultPath.exists()) {
                val bytes = defaultPath.readBytes()
                defaultFile = CachedStaticFile(
                    defaultPath,
                    bytes,
                    cacheControl(defaultPath),
                    contentType(defaultPath),
                    etag.provide(defaultPath),
                    lastModified(defaultPath),
                )
            } else {
                // file may have been deleted, so reset defaultFile & defaultCompressedFiles
                defaultFile = null
                defaultCompressedFiles = null
                return
            }

            defaultCompressedFiles = buildList {
                for (compressedType in compressedTypes) {
                    val path = defaultPath.resolveSibling("${defaultPath.pathString}.${compressedType.extension}")

                    if (path.exists()) {
                        val bytes = path.readBytes()
                        add(
                            CachedStaticFile(
                                path,
                                bytes,
                                cacheControl(defaultPath),
                                contentType(defaultPath),
                                etag.provide(path),
                                lastModified(path),
                            ) to compressedType
                        )
                    }
                }
            }.toTypedArray()
        }

        val watchService = try {
            defaultPath.parent.fileSystem.newWatchService()
        } catch (_: Exception) {
            null
        }

        val watchKey = defaultPath.parent.tryRegister(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
        )

        if (watchService != null && watchKey != null) {
            updateDefaultFile()

            val defaultCompressedPaths = compressedTypes.map { type ->
                defaultPath.resolveSibling("${defaultPath.pathString}.${type.extension}")
            }.toTypedArray()

            val job = watchForUpdates(
                watchService,
                defaultPath.parent,
                { it.isDefaultFile(defaultPath, defaultCompressedPaths) },
                { updateDefaultFile() },
            )

            application.monitor.subscribe(ApplicationStopping) {
                try {
                    runBlocking {
                        job.cancelAndJoin()
                    }
                } catch (_: Exception) {
                    // ignored
                }
                try {
                    watchService.close()
                } catch (_: ClosedWatchServiceException) {
                    // ignored
                }
            }
        }
    }

    return staticContentRoute(remotePath, autoHead) {
        if (filter(this)) return@staticContentRoute

        val relativePath = relativePath() ?: return@staticContentRoute

        respondStaticPath(
            relativePath = relativePath,
            fileSystem = fileSystem,
            index = index,
            dir = dir,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            lastModified = lastModified,
            etag = etag,
            modify = modify,
            exclude = exclude,
            extensions = extensions,
        )

        if (isHandled) return@staticContentRoute
        if (defaultPath != null) {
            val cachedFile = defaultFile
            val cachedCompressedFiles = defaultCompressedFiles
            if (cachedFile != null && cachedCompressedFiles != null) {
                // watcher was able to be registered & default file exists
                respondCachedStaticPath(defaultPath, cachedFile, cachedCompressedFiles, modify)
            } else {
                // watcher might have failed to register or default file was deleted/doesn't exist
                respondStaticPath(
                    fileSystem,
                    defaultPath,
                    compressedTypes,
                    contentType,
                    cacheControl,
                    modify,
                    lastModified,
                    etag
                )
            }
        }

        if (isHandled) return@staticContentRoute
        fallback(relativePath, this)
    }
}

private fun Path.isDefaultFile(defaultPath: Path, compressedPaths: Array<Path>): Boolean {
    return isRegularFile() &&
        (defaultPath.isSameFileAs(this) || compressedPaths.any { path -> path.isSameFileAs(this) })
}

@OptIn(DelicateCoroutinesApi::class)
private fun watchForUpdates(
    watchService: WatchService,
    relativePath: Path,
    validate: (Path) -> Boolean,
    onUpdate: () -> Unit
): Job = GlobalScope.launch(Dispatchers.IO) {
    while (this.isActive) {
        yield()

        val key = try {
            watchService.poll(100, TimeUnit.MILLISECONDS) ?: continue
        } catch (_: ClosedWatchServiceException) {
            break
        }

        for (event in key.pollEvents()) {
            // this is a safe cast, as it's always a WatchEvent<Path>,
            // unless it's a StandardWatchEventKinds.OVERFLOW.
            // but if that is the case, then we never use the context.
            @Suppress("UNCHECKED_CAST")
            event as WatchEvent<Path>

            val shouldUpdateFiles = when (event.kind()) {
                StandardWatchEventKinds.OVERFLOW -> true

                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY -> validate(relativePath.resolve(event.context()))

                else -> false
            }

            if (shouldUpdateFiles) {
                onUpdate()
                break
            }
        }

        key.reset()
    }
}

@Suppress("SameParameterValue")
private fun Path.tryRegister(watchService: WatchService?, vararg events: WatchEvent.Kind<*>): WatchKey? {
    return try {
        this.register(watchService ?: return null, events)
    } catch (_: Exception) {
        null
    }
}

private fun <Resource : Any> List<(Resource) -> Boolean>.flattenExcludeFunctions(): (Resource) -> Boolean {
    when {
        isEmpty() -> return { false }
        size == 1 -> return this.first()
        else -> return exclude@{ it ->
            for (function in this) {
                if (function(it)) {
                    return@exclude true
                }
            }

            return@exclude false
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.preCompressed)
 */
public fun Route.preCompressed(
    vararg types: CompressedFileType = CompressedFileType.entries.toTypedArray(),
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticRootFolder)
 */
@Deprecated(
    "This property only used in deprecated functions `files`, `file` and `default`. " +
        "Please use `staticFiles` or `staticResources` instead"
)
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.static)
 */
@Deprecated("Please use `staticFiles` or `staticResources` instead")
public fun Route.static(configure: Route.() -> Unit): Route = apply(configure)

/**
 * Create a block for static content at specified [remotePath]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.static)
 */
@Deprecated("Please use `staticFiles` or `staticResources` instead")
public fun Route.static(remotePath: String, configure: Route.() -> Unit): Route =
    route(remotePath, configure)

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.default)
 */

@Deprecated("Please use `staticFiles` instead")
public fun Route.default(localPath: String): Unit = default(File(localPath))

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.default)
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
    get {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.file)
 */

@Deprecated("Please use `staticFiles` instead")
public fun Route.file(remotePath: String, localPath: String = remotePath): Unit =
    file(remotePath, File(localPath))

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.file)
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
    get(remotePath) {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve all files from [folder]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.files)
 */

@Deprecated("Please use `staticFiles` instead")
public fun Route.files(folder: String): Unit = files(File(folder))

/**
 * Sets up routing to serve all files from [folder]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.files)
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        call.respondStaticFile(file, compressedTypes)
    }
}

private val staticBasePackageName = AttributeKey<String>("BasePackage")

/**
 * Base package for relative resources calculations for static content
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.staticBasePackage)
 */

@Deprecated("Please use `staticResources` instead")
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.resource)
 */

@Deprecated("Please use `staticResources` instead")
public fun Route.resource(remotePath: String, resource: String = remotePath, resourcePackage: String? = null) {
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
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
 * Sets up routing to serve all resources in [resourcePackage]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.resources)
 */

@Deprecated("Please use `staticResources` instead")
public fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.defaultResource)
 */

@Deprecated("Please use `staticResources` instead")
public fun Route.defaultResource(resource: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes?.toTypedArray() ?: emptyArray()
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.isStaticContent)
 */
public fun ApplicationCall.isStaticContent(): Boolean = attributes.contains(StaticFileLocationProperty)

private fun Route.staticContentRoute(
    remotePath: String,
    autoHead: Boolean,
    handler: suspend (ApplicationCall).() -> Unit
) = createChild(TailcardSelector).apply {
    route(remotePath) {
        route("{$pathParameterName...}") {
            get {
                call.handler()
            }
            if (autoHead) {
                method(HttpMethod.Head) {
                    install(StaticContentAutoHead)
                    handle {
                        call.handler()
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondStaticFile(
    relativePath: String,
    index: String?,
    dir: File,
    compressedTypes: Array<CompressedFileType>,
    contentType: (File) -> ContentType,
    cacheControl: (File) -> List<CacheControl>,
    lastModified: (File) -> GMTDate?,
    etag: ETagProvider,
    modify: suspend (File, ApplicationCall) -> Unit,
    exclude: (File) -> Boolean,
    extensions: Array<String>,
) {
    val requestedFile = dir.combineSafe(relativePath)

    if (exclude(requestedFile)) {
        return respond(HttpStatusCode.Forbidden)
    }

    val isDirectory = requestedFile.isDirectory
    if (index != null && isDirectory) {
        val indexFile = File(requestedFile, index)

        if (exclude(indexFile)) {
            return respond(HttpStatusCode.Forbidden)
        }

        respondStaticFile(
            indexFile,
            compressedTypes,
            contentType,
            cacheControl,
            lastModified,
            etag,
            modify
        )
    } else if (!isDirectory) {
        respondStaticFile(requestedFile, compressedTypes, contentType, cacheControl, lastModified, etag, modify)

        if (isHandled) return

        var forbiddenPath = false

        for (extension in extensions) {
            val fileWithExtension = File("${requestedFile.path}.$extension")

            if (exclude(fileWithExtension)) {
                forbiddenPath = true
                continue
            }

            respondStaticFile(fileWithExtension, compressedTypes, contentType, cacheControl, lastModified, etag, modify)

            if (isHandled) return
        }

        if (forbiddenPath) {
            return respond(HttpStatusCode.Forbidden)
        }
    }
}

private suspend fun ApplicationCall.respondStaticPath(
    relativePath: String,
    fileSystem: FileSystemPaths,
    index: Path?,
    dir: Path?,
    compressedTypes: Array<CompressedFileType>,
    contentType: (Path) -> ContentType,
    cacheControl: (Path) -> List<CacheControl>,
    lastModified: (Path) -> GMTDate?,
    etag: ETagProvider,
    modify: suspend (Path, ApplicationCall) -> Unit,
    exclude: (Path) -> Boolean,
    extensions: Array<String>,
) {
    val requestedPath = (dir ?: fileSystem.getPath("")).combineSafe(fileSystem.getPath(relativePath))

    if (exclude(requestedPath)) {
        return respond(HttpStatusCode.Forbidden)
    }

    val isDirectory = requestedPath.isDirectory()
    if (index != null && isDirectory) {
        val indexPath = requestedPath.resolve(index)

        if (exclude(indexPath)) {
            return respond(HttpStatusCode.Forbidden)
        }

        respondStaticPath(
            fileSystem,
            indexPath,
            compressedTypes,
            contentType,
            cacheControl,
            modify,
            lastModified,
            etag
        )
    } else if (!isDirectory) {
        respondStaticPath(
            fileSystem,
            requestedPath,
            compressedTypes,
            contentType,
            cacheControl,
            modify,
            lastModified,
            etag
        )

        if (isHandled) return

        var forbiddenPath = false

        for (extension in extensions) {
            val pathWithExtension = fileSystem.getPath("${requestedPath.pathString}.$extension")

            if (exclude(pathWithExtension)) {
                forbiddenPath = true
                continue
            }

            respondStaticPath(
                fileSystem,
                pathWithExtension,
                compressedTypes,
                contentType,
                cacheControl,
                modify,
                lastModified,
                etag
            )

            if (isHandled) return
        }

        if (forbiddenPath) {
            return respond(HttpStatusCode.Forbidden)
        }
    }
}

private suspend fun ApplicationCall.respondStaticResource(
    relativePath: String,
    index: String?,
    basePackage: String?,
    compressedTypes: Array<CompressedFileType>,
    contentType: (URL) -> ContentType,
    cacheControl: (URL) -> List<CacheControl>,
    lastModified: (URL) -> GMTDate?,
    etag: ETagProvider,
    modifier: suspend (URL, ApplicationCall) -> Unit,
    exclude: (URL) -> Boolean,
    extensions: Array<String>,
) {
    val relativeResourceUrl = application.resolveResourceURL(relativePath, basePackage)

    if (relativeResourceUrl != null && exclude(relativeResourceUrl)) {
        return respond(HttpStatusCode.Forbidden)
    }

    respondStaticResource(
        requestedResource = relativePath,
        packageName = basePackage,
        compressedTypes = compressedTypes,
        contentType = contentType,
        cacheControl = cacheControl,
        modifier = modifier,
        lastModified = lastModified,
        etag = etag
    )

    if (isHandled) return

    var forbiddenPath = false

    for (extension in extensions) {
        val resourceWithExtension = "$relativePath.$extension"
        val resourceWithExtensionUrl = application.resolveResourceURL(resourceWithExtension, basePackage) ?: continue

        if (exclude(resourceWithExtensionUrl)) {
            forbiddenPath = true
            continue
        }

        respondStaticResource(
            requestedResource = resourceWithExtension,
            packageName = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modifier = modifier,
            lastModified = lastModified,
            etag = etag
        )

        if (isHandled) return
    }

    if (forbiddenPath) {
        return respond(HttpStatusCode.Forbidden)
    }

    if (index != null) {
        val indexResource = "$relativePath${File.separator}$index"
        val indexResourceUrl = application.resolveResourceURL(indexResource, basePackage)

        if (indexResourceUrl != null && exclude(indexResourceUrl)) {
            return respond(HttpStatusCode.Forbidden)
        }

        respondStaticResource(
            requestedResource = indexResource,
            packageName = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modifier = modifier,
            lastModified = lastModified,
            etag = etag
        )
    }
}

private fun ApplicationCall.relativePath(): String? {
    val paths = parameters.getAll(pathParameterName) ?: return null
    return buildString(paths.sumOf { it.length } + (paths.size - 1).coerceAtLeast(0) * File.separator.length) {
        var count = 0
        for (element in paths) {
            if (++count > 1) {
                append(File.separator)
            }
            append(element)
        }
    }
}

/**
 * Wrapper on [FileSystem] for more specific delegation since we use only [getPath] method from it.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.FileSystemPaths)
 */
public interface FileSystemPaths {
    public companion object {
        /**
         * Creates a [FileSystemPaths] instance from a [FileSystem].
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.FileSystemPaths.Companion.paths)
         */
        public fun FileSystem.paths(): FileSystemPaths = object : FileSystemPaths {
            override fun getPath(first: String, vararg more: String): Path = this@paths.getPath(first, *more)
        }
    }

    /**
     * Converts a path string, or a sequence of strings that when joined form a path string, to a Path.
     * Equal to [FileSystem.getPath].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.FileSystemPaths.getPath)
     */
    public fun getPath(first: String, vararg more: String): Path
}

// Adds lower priority to the route so that it can be used as a fallback
private object TailcardSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Success(quality = RouteSelectorEvaluation.qualityTailcard)

    override fun toString(): String = "(static-content)"
}
