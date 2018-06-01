package io.ktor.util

/**
 * Specifies a key for an attribute in [Attributes]
 * @param T is type of the value stored in the attribute
 * @param name is a name of the attribute for diagnostic purposes
 */
class AttributeKey<T>(val name: String) {
    override fun toString(): String = if (name.isEmpty())
        super.toString()
    else
        "AttributeKey: $name"
}

expect class Attributes() {
    /**
     * Gets a value of the attribute for the specified [key], or throws an exception if an attribute doesn't exist
     */
    operator fun <T : Any> get(key: AttributeKey<T>): T

    /**
     * Gets a value of the attribute for the specified [key], or return `null` if an attribute doesn't exist
     */
    fun <T : Any> getOrNull(key: AttributeKey<T>): T?

    /**
     * Checks if an attribute with the specified [key] exists
     */
    operator fun contains(key: AttributeKey<*>): Boolean

    /**
     * Creates or changes an attribute with the specified [key] using [value]
     */
    fun <T : Any> put(key: AttributeKey<T>, value: T)

    /**
     * Removes an attribute with the specified [key]
     */
    fun <T : Any> remove(key: AttributeKey<T>)

    /**
     * Removes an attribute with the specified [key] and returns its current value, throws an exception if an attribute doesn't exist
     */
    fun <T : Any> take(key: AttributeKey<T>): T

    /**
     * Removes an attribute with the specified [key] and returns its current value, returns `null` if an attribute doesn't exist
     */
    fun <T : Any> takeOrNull(key: AttributeKey<T>): T?

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value
     */
    fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T

    /**
     * Returns [List] of all [AttributeKey] instances in this map
     */
    val allKeys: List<AttributeKey<*>>
}
