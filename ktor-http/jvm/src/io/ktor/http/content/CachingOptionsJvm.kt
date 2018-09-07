package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.date.*
import java.time.*

/**
 * Creates [CachingOptions] instance with [ZonedDateTime] expiration time
 */
fun CachingOptions(cacheControl: CacheControl? = null, expires: ZonedDateTime): CachingOptions =
        CachingOptions(cacheControl, expires.toGMTDate())
