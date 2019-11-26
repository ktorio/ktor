/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.streams.*
import kotlinx.cinterop.*
import platform.posix.*
import utils.*
import kotlin.time.*

internal actual class ActualFileSystem : FileSystem() {
    override val listeners: MutableCollection<FileSystemListener> = ArrayList()

    override fun openImpl(filePath: String): Output {
        mkdirP(filePath)
        val file = fopen(filePath, "a+b")
            ?: throw PosixException.forErrno(posixFunctionName = "fopen")
        return Output(file)
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        mkdirP(toPath)
        return platform.posix.rename(fromPath, toPath) == 0
    }

    override fun deleteImpl(filePath: String) {
        unlink(filePath)
    }

    override fun list(directoryPath: String): List<String> = memScoped {
        val dir = opendir(directoryPath)
        if (dir == null) {
            throw PosixException.forErrno(posixFunctionName = "opendir")
        }
        
        try {
            val entry = alloc<dirent>()
            val result = allocPointerTo<dirent>()
            return sequence<String> {
                do {
                    if (readdir_r(dir, entry.ptr, result.ptr) != 0) {
                        throw PosixException.forErrno(posixFunctionName = "readdir_r")
                    }
                    if (result.pointed == null) {
                        break
                    }
                    val name = entry.d_name.toKString()
                    yield("$directoryPath/$name")
                } while (true)
            }.toList()
        } finally {
            closedir(dir)
        }
    }

    override fun size(file: String): Long = memScoped {
        val result = alloc<stat64>()
        if (stat64(file, result.ptr) != 0) {
            val errnoResult = errno
            if (errnoResult == ENOENT) {
                return 0L
            }
            throw PosixException.forErrno(errnoResult, posixFunctionName = "stat64")
        }

        result.st_size.convert<Long>()
    }

    override fun lastModified(name: String): GMTDate = memScoped {
        val result = alloc<stat64>()
        if (stat64(name, result.ptr) != 0) {
            throw PosixException.forErrno(posixFunctionName = "stat64")
        }
        val epochMilliseconds = ktor_epoch_millis(result.ptr).convert<Long>()

        GMTDate(epochMilliseconds)
    }

    override fun contains(path: String): Boolean = memScoped {
        val result = alloc<stat64>()
        return stat64(path, result.ptr) == 0
    }

    private fun mkdirP(path: String) {
        val components = path.split("/").dropLast(1)
        for (index in 1 .. components.lastIndex) {
            val subPath = components.subList(0, index).joinToString("/")
            mkdir(subPath, 511)
        }
    }
}
