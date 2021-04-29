/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.locations

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
public data class LocationInfo internal constructor(
    val klass: KClass<*>,
    val parent: LocationInfo?,
    val parentParameter: LocationPropertyInfo?,
    val path: String,
    val pathParameters: List<LocationPropertyInfo>,
    val queryParameters: List<LocationPropertyInfo>
)

/**
 * Represents a location's property
 * @property name of the property
 * @property getter function extracting value from a location instance
 * @property isOptional when a property is optional
 */
@KtorExperimentalLocationsAPI
public abstract class LocationPropertyInfo internal constructor(
    public val name: String,
    public val isOptional: Boolean
) {
    public final override fun hashCode(): Int = name.hashCode()
    public final override fun equals(other: Any?): Boolean = other is LocationPropertyInfo &&
        name == other.name &&
        isOptional == other.isOptional

    public final override fun toString(): String = "Property(name = $name, optional = $isOptional)"
}
