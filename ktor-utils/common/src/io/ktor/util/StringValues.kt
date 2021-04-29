/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Provides data structure for associating a [String] with a [List] of Strings
 */
public interface StringValues {
    public companion object {
        /**
         * Empty [StringValues] instance
         */
        public val Empty: StringValues = StringValuesImpl()

        /**
         * Builds a [StringValues] instance with the given [builder] function
         * @param caseInsensitiveName specifies if map should have case-sensitive or case-insensitive names
         * @param builder specifies a function to build a map
         */
        public inline fun build(
            caseInsensitiveName: Boolean = false,
            builder: StringValuesBuilder.() -> Unit
        ): StringValues = StringValuesBuilder(caseInsensitiveName).apply(builder).build()
    }

    /**
     * Specifies if map has case-sensitive or case-insensitive names
     */
    public val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    public operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    public fun getAll(name: String): List<String>?

    /**
     * Gets all names from the map
     */
    public fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    public fun entries(): Set<Map.Entry<String, List<String>>>

    /**
     * Checks if the given [name] exists in the map
     */
    public operator fun contains(name: String): Boolean = getAll(name) != null

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    public fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    public fun forEach(body: (String, List<String>) -> Unit): Unit = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    public fun isEmpty(): Boolean
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public open class StringValuesSingleImpl(
    override val caseInsensitiveName: Boolean,
    public val name: String,
    public val values: List<String>
) : StringValues {

    override fun getAll(name: String): List<String>? = if (this.name.equals(name, caseInsensitiveName)) values else null

    override fun entries(): Set<Map.Entry<String, List<String>>> = setOf(
        object : Map.Entry<String, List<String>> {
            override val key: String = name
            override val value: List<String> = values
            override fun toString() = "$key=$value"
        }
    )

    override fun isEmpty(): Boolean = false

    override fun names(): Set<String> = setOf(name)

    override fun toString(): String = "StringValues(case=${!caseInsensitiveName}) ${entries()}"

    override fun hashCode(): Int = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StringValues) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun forEach(body: (String, List<String>) -> Unit): Unit = body(name, values)

    override fun get(name: String): String? =
        if (name.equals(this.name, caseInsensitiveName)) values.firstOrNull() else null

    override fun contains(name: String): Boolean = name.equals(this.name, caseInsensitiveName)

    override fun contains(name: String, value: String): Boolean =
        name.equals(this.name, caseInsensitiveName) && values.contains(value)
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public open class StringValuesImpl(
    override val caseInsensitiveName: Boolean = false,
    values: Map<String, List<String>> = emptyMap()
) : StringValues {

    protected val values: Map<String, List<String>> by lazy {
        if (caseInsensitiveName) caseInsensitiveMap<List<String>>().apply { putAll(values) } else values.toMap()
    }

    override operator fun get(name: String): String? = listForKey(name)?.firstOrNull()

    override fun getAll(name: String): List<String>? = listForKey(name)

    override operator fun contains(name: String): Boolean = listForKey(name) != null

    override fun contains(name: String, value: String): Boolean = listForKey(name)?.contains(value) ?: false

    override fun names(): Set<String> = values.keys.unmodifiable()

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun entries(): Set<Map.Entry<String, List<String>>> = values.entries.unmodifiable()

    override fun forEach(body: (String, List<String>) -> Unit) {
        for ((key, value) in values) body(key, value)
    }

    private fun listForKey(name: String): List<String>? = values[name]

    override fun toString(): String = "StringValues(case=${!caseInsensitiveName}) ${entries()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StringValues) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun hashCode(): Int = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public open class StringValuesBuilder(public val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<String>> =
        if (caseInsensitiveName) caseInsensitiveMap() else LinkedHashMap(size)

    protected var built: Boolean = false

    public fun getAll(name: String): List<String>? = values[name]

    public operator fun contains(name: String): Boolean = name in values

    public fun contains(name: String, value: String): Boolean = values[name]?.contains(value) ?: false

    public fun names(): Set<String> = values.keys

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun entries(): Set<Map.Entry<String, List<String>>> = values.entries.unmodifiable()

    public operator fun set(name: String, value: String) {
        validateValue(value)
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    public operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    public fun append(name: String, value: String) {
        validateValue(value)
        ensureListForKey(name, 1).add(value)
    }

    public fun appendAll(stringValues: StringValues) {
        stringValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    public fun appendMissing(stringValues: StringValues) {
        stringValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    public fun appendAll(name: String, values: Iterable<String>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).let { list ->
            values.forEach { value ->
                validateValue(value)
                list.add(value)
            }
        }
    }

    public fun appendMissing(name: String, values: Iterable<String>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    public fun remove(name: String) {
        values.remove(name)
    }

    public fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    public fun remove(name: String, value: String): Boolean = values[name]?.remove(value) ?: false

    public fun clear() {
        values.clear()
    }

    public open fun build(): StringValues {
        require(!built) { "ValueMapBuilder can only build a single ValueMap" }
        built = true
        return StringValuesImpl(caseInsensitiveName, values)
    }

    protected open fun validateName(name: String) {
    }

    protected open fun validateValue(value: String) {
    }

    private fun ensureListForKey(name: String, size: Int): MutableList<String> {
        if (built) {
            throw IllegalStateException(
                "Cannot modify a builder after build() function already invoked. " +
                    "Make sure you call build() last."
            )
        }

        return values[name] ?: ArrayList<String>(size).also { validateName(name); values[name] = it }
    }
}

/**
 * Build an instance of [StringValues] from a vararg list of pairs
 */
public fun valuesOf(vararg pairs: Pair<String, List<String>>, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesImpl(caseInsensitiveKey, pairs.asList().toMap())
}

/**
 * Build an instance of [StringValues] from a single pair
 */
public fun valuesOf(name: String, value: String, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesSingleImpl(caseInsensitiveKey, name, listOf(value))
}

/**
 * Build an instance of [StringValues] with a single [name] and multiple [values]
 */
public fun valuesOf(name: String, values: List<String>, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesSingleImpl(caseInsensitiveKey, name, values)
}

/**
 * Build an empty [StringValues] instance.
 */
public fun valuesOf(): StringValues = StringValues.Empty

/**
 * Build an instance of [StringValues] from the specified [map]
 */
public fun valuesOf(map: Map<String, Iterable<String>>, caseInsensitiveKey: Boolean = false): StringValues {
    val size = map.size
    if (size == 1) {
        val entry = map.entries.single()
        return StringValuesSingleImpl(caseInsensitiveKey, entry.key, entry.value.toList())
    }
    val values: MutableMap<String, List<String>> =
        if (caseInsensitiveKey) caseInsensitiveMap() else LinkedHashMap(size)
    map.entries.forEach { values.put(it.key, it.value.toList()) }
    return StringValuesImpl(caseInsensitiveKey, values)
}

/**
 * Copy values to a new independent map
 */
public fun StringValues.toMap(): Map<String, List<String>> =
    entries().associateByTo(LinkedHashMap(), { it.key }, { it.value.toList() })

/**
 * Copy values to a list of pairs
 */
public fun StringValues.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e ->
    e.value.map { e.key to it }
}

/**
 * Invoke [block] function for every value pair
 */
public fun StringValues.flattenForEach(block: (String, String) -> Unit): Unit = forEach { name, items ->
    items.forEach { block(name, it) }
}

/**
 * Create a new instance of [StringValues] filtered by the specified [predicate]
 * @param keepEmpty when `true` will keep empty lists otherwise keys with no values will be discarded
 */
public fun StringValues.filter(keepEmpty: Boolean = false, predicate: (String, String) -> Boolean): StringValues {
    val entries = entries()
    val values: MutableMap<String, MutableList<String>> =
        if (caseInsensitiveName) caseInsensitiveMap() else LinkedHashMap(entries.size)

    entries.forEach { entry ->
        val list = entry.value.filterTo(ArrayList(entry.value.size)) { predicate(entry.key, it) }
        if (keepEmpty || list.isNotEmpty()) {
            values.put(entry.key, list)
        }
    }

    return StringValuesImpl(caseInsensitiveName, values)
}

/**
 * Append values from [source] filtering values by the specified [predicate]
 * @param keepEmpty when `true` will keep empty lists otherwise keys with no values will be discarded
 */
public fun StringValuesBuilder.appendFiltered(
    source: StringValues,
    keepEmpty: Boolean = false,
    predicate: (String, String) -> Boolean
) {
    source.forEach { name, value ->
        val list = value.filterTo(ArrayList(value.size)) { predicate(name, it) }
        if (keepEmpty || list.isNotEmpty()) {
            appendAll(name, list)
        }
    }
}

/**
 * Append all values from the specified [builder]
 */
public fun StringValuesBuilder.appendAll(builder: StringValuesBuilder): StringValuesBuilder = apply {
    builder.entries().forEach { (name, values) ->
        appendAll(name, values)
    }
}

private fun entriesEquals(a: Set<Map.Entry<String, List<String>>>, b: Set<Map.Entry<String, List<String>>>): Boolean {
    return a == b
}

private fun entriesHashCode(entries: Set<Map.Entry<String, List<String>>>, seed: Int): Int {
    return seed * 31 + entries.hashCode()
}
