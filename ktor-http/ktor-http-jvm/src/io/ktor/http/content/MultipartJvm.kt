package io.ktor.http.content

import io.ktor.util.*
import java.io.*

/**
 * Provides file item's content as an [InputStream]
 */
val PartData.FileItem.streamProvider: () -> InputStream get() = { provider().asStream() }
