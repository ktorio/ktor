/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing

internal sealed class Grammar

internal interface ComplexGrammar {
    val grammars: List<Grammar>
}

internal interface SimpleGrammar {
    val grammar: Grammar
}

internal class StringGrammar(val value: String) : Grammar()
internal class AnyOfGrammar(val value: String) : Grammar()
internal class RangeGrammar(val from: Char, val to: Char) : Grammar()
internal class RawGrammar(val value: String) : Grammar()

internal class NamedGrammar(val name: String, val grammar: Grammar) : Grammar()

internal class MaybeGrammar(override val grammar: Grammar) : Grammar(), SimpleGrammar
internal class ManyGrammar(override val grammar: Grammar) : Grammar(), SimpleGrammar
internal class AtLeastOne(override val grammar: Grammar) : Grammar(), SimpleGrammar

internal class SequenceGrammar(sourceGrammars: List<Grammar>) : Grammar(), ComplexGrammar {
    override val grammars: List<Grammar> = sourceGrammars.flatten<SequenceGrammar>()
}

internal class OrGrammar(sourceGrammars: List<Grammar>) : Grammar(), ComplexGrammar {
    override val grammars: List<Grammar> = sourceGrammars.flatten<OrGrammar>()
}

internal fun maybe(grammar: Grammar): Grammar = MaybeGrammar(grammar)
internal fun maybe(value: String): Grammar = MaybeGrammar(StringGrammar(value))
internal fun maybe(block: GrammarBuilder.() -> Unit): () -> Grammar = { maybe(GrammarBuilder().apply(block).build()) }

internal infix fun String.then(grammar: Grammar): Grammar = StringGrammar(this) then grammar
internal infix fun Grammar.then(grammar: Grammar): Grammar = SequenceGrammar(listOf(this, grammar))
internal infix fun Grammar.then(value: String): Grammar = this then StringGrammar(value)

internal infix fun Grammar.or(grammar: Grammar): Grammar = OrGrammar(listOf(this, grammar))
internal infix fun Grammar.or(value: String): Grammar = this or StringGrammar(value)
internal infix fun String.or(grammar: Grammar): Grammar = StringGrammar(this) or grammar

internal fun many(grammar: Grammar): Grammar = ManyGrammar(grammar)
internal fun atLeastOne(grammar: Grammar): Grammar = AtLeastOne(grammar)

internal fun Grammar.named(name: String): Grammar = NamedGrammar(name, this)

internal fun anyOf(value: String): Grammar = AnyOfGrammar(value)
internal infix fun Char.to(other: Char): Grammar = RangeGrammar(this, other)

internal inline fun <reified T : ComplexGrammar> List<Grammar>.flatten(): List<Grammar> {
    val result = mutableListOf<Grammar>()
    forEach {
        if (it is T) result += it.grammars else result += it
    }
    return result
}
