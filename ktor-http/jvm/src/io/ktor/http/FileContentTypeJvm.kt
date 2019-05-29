/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
