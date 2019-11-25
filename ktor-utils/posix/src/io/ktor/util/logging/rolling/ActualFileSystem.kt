/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.core.*

internal actual class ActualFileSystem : FileSystem() {
    override val listeners: MutableCollection<FileSystemListener> get() = TODO("Not yet implemented")

    override fun openImpl(filePath: String): Output {
        TODO("Not yet implemented")
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun deleteImpl(filePath: String) {
        TODO("Not yet implemented")
    }

    override fun list(directoryPath: String): List<String> {
        TODO("Not yet implemented")
    }

    override fun size(file: String): Long {
        TODO("Not yet implemented")
    }

    override fun lastModified(name: String): GMTDate {
        TODO("Not yet implemented")
    }

    override fun contains(path: String): Boolean {
        TODO("Not yet implemented")
    }
}
