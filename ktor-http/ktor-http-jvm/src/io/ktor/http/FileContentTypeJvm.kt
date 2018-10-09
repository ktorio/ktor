package io.ktor.http

import io.ktor.util.*
import java.io.*
import java.nio.file.*

/**
 * @return default content type for [file] by it's extension
 */
fun ContentType.Companion.defaultForFile(file: File): ContentType =
    ContentType.fromFileExtension(file.extension).selectDefault()

/**
 * @return default content type for [file] by it's extension
 */
fun ContentType.Companion.defaultForFile(file: Path): ContentType =
    ContentType.fromFileExtension(file.extension).selectDefault()
