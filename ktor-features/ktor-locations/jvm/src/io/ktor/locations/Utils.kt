/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlinx.serialization.*

internal fun buildLocationPattern(desc: SerialDescriptor): LocationPattern {
    val children = desc.elementDescriptors().mapNotNull { child ->
        child.location?.let { buildLocationPattern(child) }
    }

    val thisPattern = when (val location = desc.location) {
        null -> buildDummyPattern(desc)
        else -> LocationPattern(location.path)
    }

    return when (children.size) {
        0 -> thisPattern
        1 -> children[0] + thisPattern
        else -> error("Multiple parents with @Location annotations")
    }
}

private fun buildDummyPattern(desc: SerialDescriptor): LocationPattern {
    return LocationPattern("/" + desc.name.substringAfterLast("."))
}

internal val SerialDescriptor.location: Location?
    get() = getEntityAnnotations().singleOrNull { it is Location } as? Location
