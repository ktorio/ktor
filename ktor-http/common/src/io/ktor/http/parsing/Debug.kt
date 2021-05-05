/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing

internal fun Grammar.printDebug(offset: Int = 0): Unit = when (this) {
    is StringGrammar -> printlnWithOffset(offset, "STRING[${Regex.escape(value)}]")
    is RawGrammar -> printlnWithOffset(offset, "STRING[$value]")
    is NamedGrammar -> {
        printlnWithOffset(offset, "NAMED[$name]")
        grammar.printDebug(offset + 2)
    }
    is SequenceGrammar -> {
        printlnWithOffset(offset, "SEQUENCE")
        grammars.forEach { it.printDebug(offset + 2) }
    }
    is OrGrammar -> {
        printlnWithOffset(offset, "OR")
        grammars.forEach { it.printDebug(offset + 2) }
    }
    is MaybeGrammar -> {
        printlnWithOffset(offset, "MAYBE")
        grammar.printDebug(offset + 2)
    }
    is ManyGrammar -> {
        printlnWithOffset(offset, "MANY")
        grammar.printDebug(offset + 2)
    }
    is AtLeastOne -> {
        printlnWithOffset(offset, "MANY_NOT_EMPTY")
        grammar.printDebug(offset + 2)
    }
    is AnyOfGrammar -> printlnWithOffset(offset, "ANY_OF[${Regex.escape(value)}]")
    is RangeGrammar -> printlnWithOffset(offset, "RANGE[$from-$to]")
}

private fun printlnWithOffset(offset: Int, node: Any) {
    println("${" ".repeat(offset)}${offset / 2}: $node")
}
