/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.nio.*
import java.io.*
import java.util.concurrent.*

internal actual class ActualFileSystem : FileSystem() {
    override val listeners: MutableCollection<FileSystemListener> = CopyOnWriteArrayList()

    override fun openImpl(filePath: String): Output {
        val file = File(filePath)
        file.parentFile.mkdirs()
        val channel = RandomAccessFile(file, "rw")
        return channel.channel.asOutput()
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        val toFile = File(toPath)
        toFile.parentFile.mkdirs()
        return File(fromPath).renameTo(toFile)
    }

    override fun deleteImpl(filePath: String) {
        File(filePath).delete()
    }

    override fun list(directoryPath: String): List<String> {
        return File(directoryPath).list()?.asList() ?: emptyList()
    }

    override fun size(file: String): Long {
        return File(file).length()
    }

    override fun lastModified(name: String): GMTDate {
        return GMTDate(File(name).lastModified())
    }

    override fun contains(path: String): Boolean {
        return File(path).exists()
    }
}
