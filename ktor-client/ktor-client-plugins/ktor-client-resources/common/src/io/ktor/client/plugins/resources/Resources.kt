/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.resources

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.util.*
import io.ktor.client.plugins.get as getFeature
import io.ktor.resources.common.Resources as ResourcesCore

/**
 * Installable feature for [ResourcesCore].
 */
public object Resources : HttpClientPlugin<ResourcesCore.Configuration, ResourcesCore> {

    override val key: AttributeKey<ResourcesCore> = AttributeKey("Resources")

    override fun prepare(block: ResourcesCore.Configuration.() -> Unit): ResourcesCore {
        val config = ResourcesCore.Configuration().apply(block)
        return ResourcesCore(config)
    }

    override fun install(plugin: ResourcesCore, scope: HttpClient) {
        // no op
    }
}

/**
 * Constructs the url for [resource].
 *
 * The class of [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> HttpClient.href(resource: T): String {
    return href(getFeature(Resources).resourcesFormat, resource)
}

/**
 * Constructs the url for [resource].
 *
 * The class of [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> HttpClient.href(resource: T, urlBuilder: URLBuilder) {
    href(getFeature(Resources).resourcesFormat, resource, urlBuilder)
}
