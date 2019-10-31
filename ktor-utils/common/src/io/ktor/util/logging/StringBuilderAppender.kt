/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Appender that buffer all logged messages that could be found later by invoking [build] function.
 */
class StringBuilderAppender(private val buffer: StringBuilder) : Appender by TextAppender({ buffer.append(it) }) {
    constructor() : this(StringBuilder(4096))

    /**
     * Creates a text from previously appended log messages
     */
    fun build(): String = buffer.toString()
}
