/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.http.Url
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlin.test.*

class UrlDeserializationTest {

    private val locationsFormat = ResourcesFormat()

    @Resource("some/path/")
    data object SimplePath

    @Test
    fun testSimplePath() {
        val pathPattern = locationsFormat.decodeFromUrl(
            SimplePath.serializer(),
            Url("some/path"),
        )
        assertEquals(SimplePath, pathPattern)
    }

    @Resource("parent/{path}")
    data class NestedClass(val path: String) {
        @Resource("{child}/path")
        data class ChildClass(val child: String, val parent: NestedClass)
    }

    @Test
    fun testNestedPath() {
        val pathPattern = locationsFormat.decodeFromUrl(NestedClass.ChildClass.serializer(),
            Url("parent/anakin/luke/path"))
        assertEquals(NestedClass.ChildClass("luke", NestedClass("anakin")), pathPattern)
    }

    @Resource("parent/{path}/")
    data class NestedClassWithSlash(val path: String) {
        @Resource("{child}/path")
        data class ChildClassWithSlash(val child: String, val parent: NestedClassWithSlash)

        @Resource("/{child}/path")
        data class ChildClassWithoutSlash(val child: String, val parent: NestedClassWithSlash)
    }

    @Resource("parent/{path}")
    data class NestedClassWithoutSlash(val path: String) {
        @Resource("/{child}/path")
        data class ChildClassWithSlash(val child: String, val parent: NestedClassWithoutSlash)
    }

    @Test
    fun testNestedPathDoesNotDoubleSlash() {
        val pathPattern1 = locationsFormat.decodeFromUrl(
            NestedClassWithSlash.ChildClassWithSlash.serializer(),
            Url("parent/anakin/luke/path")
        )
        assertEquals(
            NestedClassWithSlash.ChildClassWithSlash(
                "luke", NestedClassWithSlash("anakin"),
            ),
            pathPattern1,
        )

        val pathPattern3 = locationsFormat.decodeFromUrl(
            NestedClassWithSlash.ChildClassWithoutSlash.serializer(),
            Url("parent/anakin/luke/path"),
        )
        assertEquals(
            NestedClassWithSlash.ChildClassWithoutSlash("luke", NestedClassWithSlash("anakin")),
            pathPattern3,
        )

        val pathPattern2 = locationsFormat.decodeFromUrl(
            NestedClassWithoutSlash.ChildClassWithSlash.serializer(),
            Url("parent/anakin/luke/path"),
        )
        assertEquals(NestedClassWithoutSlash.ChildClassWithSlash(
            "luke",
            NestedClassWithoutSlash("anakin"),
        ), pathPattern2)
    }

    @Resource("/{child}/path")
    data class Container(
        val child: String,
        val parent: MultipleParents
    )

    @Resource("/{value}/path")
    data class MultipleParents(val parent1: NestedClass, val value: String, val parent2: NestedClassWithSlash)

    @Test
    fun testMultipleNestedParentsShouldThrow() {
        assertFailsWith<ResourceSerializationException> {
            locationsFormat.decodeFromUrl(Container.serializer(), Url("/anakin/path/luke/path"))
        }.let {
            assertEquals(
                "There are multiple parents for resource " +
                    "io.ktor.tests.resources.UrlDeserializationTest.MultipleParents",
                it.message
            )
        }
    }

    @Resource("v1")
    private data object V1 {
        @Resource("api")
        data class Api(val parent: V1) {
            @Resource("users/{name}")
            data class User(val name: String, val parent: Api, val search: String? = null)

            @Resource("users/{name?}")
            data class OptionalUser(val name: String? = null, val parent: Api, val search: String? = null)

            @Resource("users/{names...}")
            data class TrailingUser(val names: List<String> = emptyList(), val parent: Api, val search: String? = null)
        }
    }

    @Test
    fun decodeWithQueryParameters() {
        val user = V1.Api.User("tony", V1.Api(V1), "todos")
        val decoded = locationsFormat.decodeFromUrl(V1.Api.User.serializer(), Url("/v1/api/users/tony?search=todos"))

        assertEquals(user, decoded)
    }

    @Test
    fun decodeOptionalWithQueryParameters() {
        val user = V1.Api.OptionalUser("tony", V1.Api(V1), "todos")
        val decoded = locationsFormat.decodeFromUrl(V1.Api.OptionalUser.serializer(), Url("/v1/api/users/tony?search=todos"))

        assertEquals(user, decoded)

        val missing = V1.Api.OptionalUser(null, V1.Api(V1), "todos")
        val missingDecoded = locationsFormat.decodeFromUrl(V1.Api.OptionalUser.serializer(), Url("/v1/api/users?search=todos"))

        assertEquals(missing, missingDecoded)
    }

    @Test
    fun decodeTrailingWithQueryParameters() {
        val user = V1.Api.TrailingUser(listOf("tony"), V1.Api(V1), "todos")
        val decoded = locationsFormat.decodeFromUrl(V1.Api.TrailingUser.serializer(), Url("/v1/api/users/tony?search=todos"))

        assertEquals(user, decoded)

        val missing = V1.Api.TrailingUser(emptyList(), V1.Api(V1), "todos")
        val missingDecoded = locationsFormat.decodeFromUrl(V1.Api.TrailingUser.serializer(), Url("/v1/api/users?search=todos"))

        assertEquals(missing, missingDecoded)

        val missingMultiple = V1.Api.TrailingUser(listOf("tony", "stark"), V1.Api(V1), "todos")
        val missingMultipleDecoded = locationsFormat.decodeFromUrl(V1.Api.TrailingUser.serializer(), Url("/v1/api/users/tony/stark?search=todos"))

        assertEquals(missingMultiple, missingMultipleDecoded)
    }
}
