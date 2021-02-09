/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.resources

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.resources.common.Resources as ResourcesCore

/**
 * Installable feature for [ResourcesCore].
 */
public object Resources : ApplicationPlugin<Application, ResourcesCore.Configuration, ResourcesCore> {

    override val key: AttributeKey<ResourcesCore> = AttributeKey("Resources")

    override fun install(pipeline: Application, configure: ResourcesCore.Configuration.() -> Unit): ResourcesCore {
        val configuration = ResourcesCore.Configuration().apply(configure)
        return ResourcesCore(configuration)
    }
}

/**
 * Constructs the url for [resource].
 *
 * The class of [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> Application.href(resource: T): String {
    return href(plugin(Resources).resourcesFormat, resource)
}

/**
 * Constructs the url for [resource].
 *
 * The class of [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> Application.href(resource: T, urlBuilder: URLBuilder) {
    href(plugin(Resources).resourcesFormat, resource, urlBuilder)
}
