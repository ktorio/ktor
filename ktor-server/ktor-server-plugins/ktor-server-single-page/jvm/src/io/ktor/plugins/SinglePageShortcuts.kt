/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.plugins

import io.ktor.server.application.*

/**
 * Configures an Angular single page application with resources served from [filesPath]. Default page is index.html.
 */
public fun Application.configureAngularSinglePageApplication(filesPath: String): Unit
    = configureSinglePageApplication(filesPath)

/**
 * Configures a Vue single page application with resources served from [filesPath]. Default page is index.html.
 */
public fun Application.configureVueSinglePageApplication(filesPath: String): Unit
    = configureSinglePageApplication(filesPath)

/**
 * Configures a React single page application with resources served from [filesPath]. Default page is index.html.
 */
public fun Application.configureReactSinglePageApplication(filesPath: String): Unit
    = configureSinglePageApplication(filesPath)

/**
 * Configures a Backbone single page application with resources served from [filesPath]. Default page is index.html.
 */
public fun Application.configureBackboneSinglePageApplication(filesPath: String): Unit
    = configureSinglePageApplication(filesPath)

/**
 * Configures an Ember single page application with resources served from [filesPath]. Default page is index.html.
 */
public fun Application.configureEmberSinglePageApplication(filesPath: String): Unit
    = configureSinglePageApplication(filesPath)

internal fun Application.configureSinglePageApplication(filesPath: String) {
    install(SinglePageApplication) {
        this.filesPath = filesPath
    }
}
