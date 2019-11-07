/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import io.ktor.util.logging.*

/**
 * Add log locations (expensive)
 */
fun LoggingConfigBuilder.locations(
    format: (LocationEntry) -> String = ::defaultLocationFormat,
    predicate: (List<LocationEntry>) -> LocationEntry? = ::defaultLocationPredicate
) {
    val key = LocationsKey()
    registerKey(key)

    enrich {
        this[key] = currentLocation()
    }

    label {
        predicate(it[key])?.let { location ->
            append(format(location))
        }
    }
}

/**
 * Add log locations (expensive)
 */
fun LoggingConfigBuilder.locationsPure() {
    val key = LocationsKey()
    registerKey(key)

    enrich {
        this[key] = currentLocation()
    }
}

fun LoggingConfigBuilder.ensureLocations() {
    if (keys.none { it is LocationsKey }) {
        locationsPure()
    }
}

val LogRecord.locations: List<LocationEntry>
    get() = config.findKey<LocationsKey>()?.let { key -> this[key] } ?: emptyList()

/**
 * Represents location
 */
class LocationEntry(val location: String, val file: String?, val line: Int?)

fun LocationEntry.isLoggingInternals(): Boolean {
    return location.startsWith("io.ktor.util.logging.")
}

fun defaultLocationPredicate(entries: List<LocationEntry>): LocationEntry? =
    entries.firstOrNull { !it.isLoggingInternals() }

fun defaultLocationFormat(entry: LocationEntry): String = "${entry.location} (${entry.file}:${entry.line})"

private class LocationsKey : LogAttributeKey<List<LocationEntry>>("location", emptyList())

private fun currentLocation(): List<LocationEntry> {
    return Throwable().apply {
        fillInStackTrace()
    }.stackTrace.map { trace ->
        LocationEntry(
            "${trace.className}.${trace.methodName}",
            trace.fileName,
            trace.lineNumber.takeUnless { it == -1 })
    }
}
