package io.ktor.http.content

import io.ktor.util.*
import java.io.*

val PartData.FileItem.streamProvider: () -> InputStream get() = { provider().asStream() }
