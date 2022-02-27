/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.resources

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.util.*
import io.ktor.resources.Resources as ResourcesCore

/**
 * Adds support for type-safe requests using [ResourcesCore].
 *
 * Example:
 * ```kotlin
 * @Serializable
 * @Resource("/users")
 * data class Users {
 *   @Serializable
 *   @Resource("/{id}")
 *   data class ById(val parent: Users = Users(), val id: Long)
 *
 *   @Serializable
 *   @Resource("/add")
 *   data class Add(val parent: Users = Users(), val name: String)
 * }
 *
 * // client-side
 * val newUserId = client.post(Users.Add("new_user")) // "/users?name=new_user"
 * val addedUser = client.get(Users.ById(newUserId)) // "/user/123"
 * ```
 *
 * @see Resource
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
 * Constructs a URL for [resource].
 *
 * The class of the [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> HttpClient.href(resource: T): String {
    return href(plugin(Resources).resourcesFormat, resource)
}

/**
 * Constructs a URL for [resource].
 *
 * The class of the [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T : Any> HttpClient.href(resource: T, urlBuilder: URLBuilder) {
    href(plugin(Resources).resourcesFormat, resource, urlBuilder)
}
