/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cache.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File

/**
 * Creates storage that uses file system to store cache data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cache.storage.FileStorage)
 *
 * @param directory directory to store cache data.
 * @param dispatcher dispatcher to use for file operations.
 */
@Suppress("FunctionName")
@Deprecated("Use the version from the common package using kotlinx.io")
public fun FileStorage(
    directory: File,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): CacheStorage = FileStorage(
    fileSystem = SystemFileSystem,
    directory = Path(directory.path),
    dispatcher = dispatcher,
)
