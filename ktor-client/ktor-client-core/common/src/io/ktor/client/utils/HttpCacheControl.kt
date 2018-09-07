package io.ktor.client.utils

import io.ktor.util.*

/**
 * List of [CacheControl] known values.
 */
@KtorExperimentalAPI
object CacheControl {
    const val MAX_AGE = "max-age"
    const val MIN_FRESH = "min-fresh"
    const val ONLY_IF_CACHED = "only-if-cached"

    const val MAX_STALE = "max-stale"
    const val NO_CACHE = "no-cache"
    const val NO_STORE = "no-store"
    const val NO_TRANSFORM = "no-transform"

    const val MUST_REVALIDATE = "must-revalidate"
    const val PUBLIC = "public"
    const val PRIVATE = "private"
    const val PROXY_REVALIDATE = "proxy-revalidate"
    const val S_MAX_AGE = "s-maxage"

    // ------- binary compatibility

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getMAX_AGE(): String = MAX_AGE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getMIN_FRESH(): String = MIN_FRESH

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getONLY_IF_CACHED(): String = ONLY_IF_CACHED

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getMAX_STALE(): String = MAX_STALE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getNO_CACHE(): String = NO_CACHE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getNO_STORE(): String = NO_STORE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getNO_TRANSFORM(): String = NO_TRANSFORM

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getMUST_REVALIDATE(): String = MUST_REVALIDATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getPUBLIC(): String = PUBLIC

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getPRIVATE(): String = PRIVATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getPROXY_REVALIDATE(): String = PROXY_REVALIDATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getS_MAX_AGE(): String = S_MAX_AGE
}
