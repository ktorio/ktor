package io.ktor.http

import io.ktor.util.*

/**
 * Represents HTTP headers as a map from case-insensitive names to collection of [String] values
 */
interface Headers : StringValues {
    companion object {
        /**
         * Empty [Headers] instance
         */
        @Suppress("DEPRECATION")
        val Empty: Headers = EmptyHeaders

        /**
         * Builds a [Headers] instance with the given [builder] function
         * @param builder specifies a function to build a map
         */
        inline fun build(builder: HeadersBuilder.() -> Unit): Headers = HeadersBuilder().apply(builder).build()
    }
}

@Suppress("KDocMissingDocumentation")
class HeadersBuilder(size: Int = 8) : StringValuesBuilder(true, size) {
    override fun build(): Headers {
        require(!built) { "HeadersBuilder can only build a single Headers instance" }
        built = true
        return HeadersImpl(values)
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated("Empty headers is internal", replaceWith = ReplaceWith("Headers.Empty"))
object EmptyHeaders : Headers {
    override val caseInsensitiveName: Boolean get() = true
    override fun getAll(name: String): List<String>? = null
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<String>>> = emptySet()
    override fun isEmpty(): Boolean = true
    override fun toString(): String = "Headers ${entries()}"
}

/**
 * Returns empty headers
 */
fun headersOf(): Headers = Headers.Empty

/**
 * Returns [Headers] instance containing only one header with the specified [name] and [value]
 */
fun headersOf(name: String, value: String): Headers = HeadersSingleImpl(name, listOf(value))

/**
 * Returns [Headers] instance containing only one header with the specified [name] and [values]
 */
fun headersOf(name: String, values: List<String>): Headers = HeadersSingleImpl(name, values)

/**
 * Returns [Headers] instance from [pairs]
 */
fun headersOf(vararg pairs: Pair<String, List<String>>): Headers = HeadersImpl(pairs.asList().toMap())

@InternalAPI
@Suppress("KDocMissingDocumentation")
class HeadersImpl(values: Map<String, List<String>> = emptyMap()) : Headers, StringValuesImpl(true, values) {
    override fun toString(): String = "Headers ${entries()}"
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
class HeadersSingleImpl(name: String, values: List<String>) : Headers, StringValuesSingleImpl(true, name, values) {
    override fun toString(): String = "Headers ${entries()}"
}


