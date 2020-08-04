/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlin.reflect.*

/**
 * A location class/object registration info.
 * @property klassOrNull of the location class/object
 * @property parent is a registered outer location class
 * @property parentParameter is a property for an outer class
 * @property path at which this location is registered
 * @property pathParameters is a list of properties stored in path components
 * @property queryParameters is a list of properties stored in query parameters
 */
@KtorExperimentalLocationsAPI
public expect class LocationInfo @InternalAPI public constructor(
    klass: KClass<*>?,
    parent: LocationInfo?,
    parentParameter: LocationPropertyInfo?,
    path: String,
    pathParameters: List<LocationPropertyInfo>,
    queryParameters: List<LocationPropertyInfo>,
    serialDescriptor: SerialDescriptor
) {
    @InternalAPI
    public val klassOrNull: KClass<*>?

    public val parent: LocationInfo?
    public val parentParameter: LocationPropertyInfo?
    public val path: String
    public val pathParameters: List<LocationPropertyInfo>
    public val queryParameters: List<LocationPropertyInfo>
    public val serialDescriptor: SerialDescriptor

    public override fun equals(other: Any?): Boolean
    public override fun hashCode(): Int
}

/**
 * Represents a location's property
 * @property name of the property
 * @property isOptional when a property is optional
 */
@KtorExperimentalLocationsAPI
public abstract class LocationPropertyInfo @InternalAPI public constructor(
    public val name: String,
    public val isOptional: Boolean
) {
    final override fun hashCode(): Int = name.hashCode()
    final override fun equals(other: Any?): Boolean = other is LocationPropertyInfo &&
        name == other.name &&
        isOptional == other.isOptional

    final override fun toString(): String = "Property(name = $name, optional = $isOptional)"
}
