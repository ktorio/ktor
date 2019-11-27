/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import org.khronos.webgl.*
import kotlin.math.*

internal actual class ActualFileSystem : FileSystem() {
    private val nodeFileSystem: NodeFileSystem = require("fs")
//    private val nodePath: NodePath = require("path")
    private val cwd: String = require<NodeProcess>("process").cwd()

    override val listeners: MutableCollection<FileSystemListener> = ArrayList()

    override fun openImpl(filePath: String): Output {
        val resolvedFilePath = filePath.resolved()
        mkdirP(resolvedFilePath)

        var fd = try {
            nodeFileSystem.openSync(resolvedFilePath, "a", 438)
        } catch (e: dynamic) {
            throw IOException("Failed to open file $filePath: $e")
        }

        return object : AbstractOutput() {
            override fun closeDestination() {
                if (fd != -1) {
                    nodeFileSystem.closeSync(fd)
                    fd = -1
                }
            }

            override fun flush(source: Memory, offset: Int, length: Int) {
                if (fd == -1) {
                    throw IOException("File is already closed")
                }
                try {
                    nodeFileSystem.writeSync(fd, source.view, offset, length)
                } catch (e: dynamic) {
                    throw IOException("Failed to write to file $filePath $length bytes: $e")
                }
            }
        }
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        return try {
            mkdirP(toPath.resolved())
            nodeFileSystem.renameSync(fromPath.resolved(), toPath.resolved())
            true
        } catch (_: dynamic) {
            false
        }
    }

    override fun deleteImpl(filePath: String) {
        try {
            nodeFileSystem.unlinkSync(filePath.resolved())
        } catch (_: dynamic) {
        }
    }

    override fun list(directoryPath: String): List<String> {
        try {
            return nodeFileSystem.readdirSync(directoryPath.resolved()).map { "$directoryPath/$it" }
        } catch (e: dynamic) {
            throw IOException("Failed to read directory $directoryPath: $e")
        }
    }

    override fun size(file: String): Long {
        return try {
            nodeFileSystem.statSync(file.resolved()).size.toLong()
        } catch (_: dynamic) {
            0
        }
    }

    override fun lastModified(name: String): GMTDate {
        return try {
            GMTDate(nodeFileSystem.statSync(name.resolved()).mtimeMs.roundToLong())
        } catch (e: dynamic) {
            throw IOException("Failed to find last modification date for $name: $e")
        }
    }

    override fun contains(path: String): Boolean {
        return try {
            nodeFileSystem.existsSync(path.resolved())
        } catch (_: dynamic) {
            false
        }
    }

    private fun mkdirP(path: String) {
        val components = path.split("/")
        for (index in 1 .. components.lastIndex) {
            val subPath = components.subList(0, index).joinToString("/")
            try {
                nodeFileSystem.mkdirSync(subPath)
            } catch (_: dynamic) {
            }
        }
    }

    private fun String.resolved(): String = "$cwd/$this"
}

private external interface NodeFileSystem {
    fun existsSync(path: String): Boolean
    fun mkdirSync(path: String)
    fun renameSync(from: String, to: String)
    fun unlinkSync(path: String)
    fun readdirSync(path: String): Array<String>
    fun writeSync(fd: Int, buffer: DataView, offset: Int, length: Int): Int
    fun openSync(path: String, flags: String, mode: Int): Int
    fun closeSync(fd: Int)
    fun statSync(path: String): NodeStat
}

private external interface NodeStat {
    val size: Int
    val mtimeMs: Double
}

private external interface NodeProcess {
    fun cwd(): String
}

private external fun <T> require(module: String): T
