/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

/**
 * List of [CacheControl] known values.
 */
@Suppress("KDocMissingDocumentation", "MemberVisibilityCanBePrivate")
public object CacheControl {
    public const val MAX_AGE: String = "max-age"
    public const val MIN_FRESH: String = "min-fresh"
    public const val ONLY_IF_CACHED: String = "only-if-cached"

    public const val MAX_STALE: String = "max-stale"
    public const val NO_CACHE: String = "no-cache"
    public const val NO_STORE: String = "no-store"
    public const val NO_TRANSFORM: String = "no-transform"

    public const val MUST_REVALIDATE: String = "must-revalidate"
    public const val PUBLIC: String = "public"
    public const val PRIVATE: String = "private"
    public const val PROXY_REVALIDATE: String = "proxy-revalidate"
    public const val S_MAX_AGE: String = "s-maxage"

    // ------- binary compatibility

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getMAX_AGE(): String = MAX_AGE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getMIN_FRESH(): String = MIN_FRESH

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getONLY_IF_CACHED(): String = ONLY_IF_CACHED

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getMAX_STALE(): String = MAX_STALE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getNO_CACHE(): String = NO_CACHE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getNO_STORE(): String = NO_STORE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getNO_TRANSFORM(): String = NO_TRANSFORM

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getMUST_REVALIDATE(): String = MUST_REVALIDATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getPUBLIC(): String = PUBLIC

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getPRIVATE(): String = PRIVATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getPROXY_REVALIDATE(): String = PROXY_REVALIDATE

    @Suppress("unused", "KDocMissingDocumentation", "FunctionName")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    public fun getS_MAX_AGE(): String = S_MAX_AGE
}
