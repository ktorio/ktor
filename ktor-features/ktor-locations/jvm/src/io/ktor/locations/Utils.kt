/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlinx.serialization.*

internal fun buildLocationPattern(desc: SerialDescriptor): LocationPattern {
    val location = desc.location ?: error("No @Location annotation found for ${desc.name}")
    val children = desc.elementDescriptors().mapNotNull { child ->
        child.location?.let { buildLocationPattern(child) }
    }

    return when (children.size) {
        0 -> LocationPattern(location.path)
        1 -> children[0] + LocationPattern(location.path)
        else -> error("Multiple parents with @Location annotations")
    }
}

internal val SerialDescriptor.location: Location?
    get() = getEntityAnnotations().singleOrNull { it is Location } as? Location
