package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.date.*
import java.time.*

fun CachingOptions(cacheControl: CacheControl? = null, expires: ZonedDateTime? = null) =
        CachingOptions(cacheControl, expires?.toGMTDate())
