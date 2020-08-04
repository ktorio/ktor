/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.descriptors.*
import kotlin.reflect.*

/**
 * A location class/object registration info.
 * @property klass of the location class/object
 * @property parent is a registered outer location class
 * @property parentParameter is a property for an outer class
 * @property path at which this location is registered
 * @property pathParameters is a list of properties stored in path components
 * @property queryParameters is a list of properties stored in query parameters
 */
@KtorExperimentalLocationsAPI
public actual class LocationInfo @InternalAPI public actual constructor(
    klass: KClass<*>?,
    public actual val parent: LocationInfo?,
    public actual val parentParameter: LocationPropertyInfo?,
    public actual val path: String,
    public actual val pathParameters: List<LocationPropertyInfo>,
    public actual val queryParameters: List<LocationPropertyInfo>,
    public actual val serialDescriptor: SerialDescriptor
) {
    public val klass: KClass<*> = klass!!

    @InternalAPI
    public actual val klassOrNull: KClass<*>? get() = klass

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocationInfo) return false

        if (parent != other.parent) return false
        if (parentParameter != other.parentParameter) return false
        if (path != other.path) return false
        if (pathParameters != other.pathParameters) return false
        if (queryParameters != other.queryParameters) return false
        if (serialDescriptor != other.serialDescriptor) return false

        return true
    }

    actual override fun hashCode(): Int {
        var result = parent?.hashCode() ?: 0
        result = 31 * result + (parentParameter?.hashCode() ?: 0)
        result = 31 * result + path.hashCode()
        result = 31 * result + pathParameters.hashCode()
        result = 31 * result + queryParameters.hashCode()
        result = 31 * result + serialDescriptor.hashCode()
        return result
    }


}
