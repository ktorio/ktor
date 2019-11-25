/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.core.*

internal actual class ActualFileSystem : FileSystem() {
    override val listeners: MutableCollection<FileSystemListener> = ArrayList()

    override fun openImpl(filePath: String): Output {
        error("Unable to open file at JS platform")
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        return false
    }

    override fun deleteImpl(filePath: String) {
    }

    override fun list(directoryPath: String): List<String> {
        return emptyList()
    }

    override fun size(file: String): Long {
        return 0
    }

    override fun lastModified(name: String): GMTDate {
        error("Unable to check file's last modification date at JS platform")
    }

    override fun contains(path: String): Boolean {
        return false
    }
}
