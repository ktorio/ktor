/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.utils.io.core.*

internal abstract class FileSystem {
    protected abstract val listeners: MutableCollection<FileSystemListener>

    protected abstract fun openImpl(filePath: String): Output
    protected abstract fun renameImpl(fromPath: String, toPath: String): Boolean
    protected abstract fun deleteImpl(filePath: String)

    fun open(fileName: String): Output = openImpl(fileName)

    fun rename(fromPath: String, toPath: String): Boolean = when {
        renameImpl(fromPath, toPath) -> {
            listeners.forEach {
                it.fileRemoved(fromPath)
                it.fileCreated(toPath)
            }
            true
        }
        else -> false
    }

    fun delete(filePath: String) {
        deleteImpl(filePath)
        listeners.forEach {
            it.fileRemoved(filePath)
        }
    }

    abstract fun list(directoryPath: String): List<String>

    abstract fun size(file: String): Long

    abstract fun lastModified(name: String): GMTDate

    abstract fun contains(path: String): Boolean

    fun addListener(listener: FileSystemListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: FileSystemListener) {
        listeners.remove(listener)
    }
}

internal interface FileSystemListener {
    fun fileCreated(path: String)
    fun fileRemoved(path: String)
    fun fileUpdated(path: String)
}

internal fun FileSystem.list(
    filePathPattern: FilePathPattern
): Sequence<String> {
    val first = filePathPattern.pathComponentsPatterns.constantPrefix()
    var remaining = filePathPattern.pathComponentsPatterns.drop(first.size)

    var current: Sequence<String> = when {
        first.isNotEmpty() -> sequenceOf(first.joinToString("/") { it.text }).filter { contains(it) }
        else -> sequenceOf(".")
    }

    while (remaining.isNotEmpty()) {
        @Suppress("UNCHECKED_CAST")
        val constantPrefix = remaining.constantPrefix()
        val nonConstantTail = remaining.drop(constantPrefix.size)

        val roots: Sequence<String> = when {
            constantPrefix.isNotEmpty() -> current.map { root ->
                constantPrefix.joinToString(
                    "/",
                    prefix = "$root/"
                ) { it.text }
            }.filter {
                contains(it)
            }
            else -> current
        }

        when {
            nonConstantTail.isEmpty() -> {
                current = roots
                remaining = nonConstantTail
            }
            else -> {
                val pattern = nonConstantTail.first() as FilePathPattern.PatternOrConstant.Pattern
                current = roots.flatMap { root ->
                    list(root).filter { it.substringAfterLast('/', it).matches(pattern.regex) }.asSequence()
                }
                remaining = nonConstantTail.drop(1)
            }
        }
    }

    return current
}

private fun List<FilePathPattern.PatternOrConstant>.constantPrefix(): List<FilePathPattern.PatternOrConstant.Constant> =
    takeWhileIsInstance()

@Suppress("UNCHECKED_CAST")
private inline fun <reified C> List<*>.takeWhileIsInstance(): List<C> = takeWhile { it is C } as List<C>
