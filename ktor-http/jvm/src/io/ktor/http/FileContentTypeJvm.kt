/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.io.File
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.defaultForFile)
 *
 * @return default content type for [file] by its extension
 */
public fun ContentType.Companion.defaultForFile(file: File): ContentType =
    ContentType.fromFileExtension(file.extension).selectDefault()

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.defaultForPath)
 *
 * @return default content type for [path] by its extension
 */
public fun ContentType.Companion.defaultForPath(path: Path): ContentType =
    ContentType.fromFileExtension(path.extension).selectDefault()
