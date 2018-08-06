package io.ktor.http.parsing

internal class GrammarBuilder() {
    private val grammars = mutableListOf<Grammar>()

    infix fun then(grammar: Grammar): GrammarBuilder {
        grammars += grammar
        return this
    }

    infix fun then(value: String): GrammarBuilder {
        grammars += StringGrammar(value)
        return this
    }

    operator fun (() -> Grammar).unaryPlus(): Unit {
        grammars += this()
    }

    operator fun Grammar.unaryPlus(): Unit {
        grammars += this
    }

    operator fun String.unaryPlus(): Unit {
        grammars += StringGrammar(this)
    }

    fun build(): Grammar = if (grammars.size == 1) grammars.first() else SequenceGrammar(grammars)
}

internal fun grammar(block: GrammarBuilder.() -> Unit): Grammar = GrammarBuilder().apply(block).build()
