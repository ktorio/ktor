/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlinx.serialization.*

/**
 * API marked with this annotation is experimental and is not guaranteed to be stable.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This locations API is experimental. It could be changed or removed in future releases."
)
@Experimental(level = Experimental.Level.WARNING)
public annotation class KtorExperimentalLocationsAPI

/**
 * Annotation for classes that will act as typed routes.
 * @property path the route path, including class property names wrapped with curly braces.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
@SerialInfo
public annotation class Location(public val path: String)

/**
 * Marker interface providing a way to specify location's return type.
 */
@KtorExperimentalLocationsAPI
public interface RespondsWith<R>

