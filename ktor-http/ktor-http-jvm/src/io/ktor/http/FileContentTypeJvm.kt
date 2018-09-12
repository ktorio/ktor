package io.ktor.http

import io.ktor.util.*
import java.io.*
import java.nio.file.*

private val contentTypesFileName = "mimelist.csv"

fun ContentType.Companion.defaultForFile(file: File): ContentType =
    ContentType.fromFileExtension(file.extension).selectDefault()

fun ContentType.Companion.defaultForFile(file: Path): ContentType =
    ContentType.fromFileExtension(file.extension).selectDefault()
