/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import kotlinx.serialization.*

/**
 * Annotation for classes that will act as typed routes. All annotated types should be [Serializable].
 *
 * Every property that has a corresponding placeholder inside [path] will be used as a value for this placeholder.
 * Other properties will be put into url query.
 * Example:
 * ```
 * @Serializable
 * @Resource("/users/{id}")
 * data class UserById(val id: Long, val properties: List<String>)
 *
 * val userById = UserById(id = 123, properties = listOf("name", "avatar"))
 * val url = href(userById)
 * assertEquals("/users/123?properties=name&properties=avatar")
 * ```
 * Properties can be primitives or types annotated with [Serializable] annotation.
 *
 * You can nest class for better organization, but all nested classes should have property with a type of outer class.
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
 * val userById = Users.ById(123)
 * val addUser = Users.add("new_name")
 * ```
 *
 * @property path the route path, including class property names wrapped with curly braces.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
public annotation class Resource(val path: String)
