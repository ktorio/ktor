/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*

/**
 * Extension interface for dependency maps.
 *
 * This can be used for supplying services through external means via a service locator for 3rd party libraries.
 *
 * To do this, you must include a file in your resources under:
 * META-INF/services/io.ktor.server.plugins.di.DependencyMapExtension
 *
 * The file should contain the full name of the implementing class.
 *
 * The implementing class must have a public constructor with no arguments.
 */
public interface DependencyMapExtension {
    /**
     * Gets the dependency map for the specified application.
     *
     * This will be executed upon the first call to the [DependencyRegistry] for the application.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyMapExtension.get)
     */
    public fun get(application: Application): DependencyMap
}
