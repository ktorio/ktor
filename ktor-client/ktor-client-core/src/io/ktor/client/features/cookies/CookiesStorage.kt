package io.ktor.client.features.cookies

import io.ktor.http.*

/**
 * Storage for [Cookie].
 */
interface CookiesStorage {
    /**
     * Gets a map of [String] to [Cookie] for a specific [host].
     */
    suspend fun get(host: String): Map<String, Cookie>?

    /**
     * Try to get a [Cookie] with the specified cookie's [name] for a [host].
     */
    suspend fun get(host: String, name: String): Cookie?

    /**
     * Sets a [cookie] for the specified [host].
     */
    suspend fun addCookie(host: String, cookie: Cookie)
}

/**
 * Runs a [block] of code, for all the cookies set in the specified [host].
 */
suspend inline fun CookiesStorage.forEach(host: String, block: (Cookie) -> Unit) {
    get(host)?.forEach { block(it.value) }
}
