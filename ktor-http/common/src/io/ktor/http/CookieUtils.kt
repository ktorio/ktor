/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.date.*

internal class StringLexer(val source: String) {
    var index = 0

    fun test(predicate: (Char) -> Boolean): Boolean =
        index < source.length && predicate(source[index])

    fun accept(predicate: (Char) -> Boolean): Boolean =
        test(predicate).also { if (it) index++ }

    fun acceptWhile(predicate: (Char) -> Boolean): Boolean {
        if (!test(predicate)) return false
        while (test(predicate)) index++
        return true
    }

    fun capture(block: StringLexer.() -> Unit): String {
        val start = index
        block()
        return source.substring(start, index)
    }
}

class CookieDateParser {

    fun parse(source: String): GMTDate = TODO()
}
