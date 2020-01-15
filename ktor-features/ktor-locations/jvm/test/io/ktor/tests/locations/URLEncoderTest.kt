/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.locations.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.test.*

@UseExperimental(ImplicitReflectionSerializer::class)
class URLEncoderTest {
    private val encoder = URLEncoder(EmptyModule)

    @Test
    fun urlParameter() {
        assertEquals("http://localhost/path?p=a", serialize(URLParameter("a")))
    }

    @Test
    fun pathParameter() {
        assertEquals("http://localhost/path/b", serialize(PathParameter("b")))
    }

    @Test
    fun parentChild() {
        assertEquals("http://localhost/path/777?b=1&b=2", serialize(Xy("777", "1", C("2"))))
    }

    @Test
    fun parentChildBothLocations() {
        assertEquals("http://localhost/root/1/child/2", serialize(Root.Child("2", Root("1"))))
    }

    @Test
    fun testEllipsis() {
        assertEquals("http://localhost/a/b/c", serialize(Ellipsis(listOf("a", "b", "c"))))
    }

    private inline fun <reified T> serialize(instance: T): String {
        serializer<T>().serialize(encoder, instance)
        return encoder.build().toString()
    }
}

@Serializable
private data class C(val b: String)

@Serializable
@Location("/path/{a}")
private data class Xy(val a: String, val b: String, val c: C)

@Serializable
@Location("/path")
private data class URLParameter(val p: String)

@Serializable
@Location("/path/{p}")
private data class PathParameter(val p: String)

@Serializable
@Location("/root/{a}")
private class Root(val a: String) {
    @Serializable
    @Location("/child/{b}")
    class Child(val b: String, val root: Root)
}

@Serializable
@Location("/{param...}")
private class Ellipsis(val param: List<String>)
