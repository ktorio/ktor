/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.http.*
import io.ktor.locations.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.test.*

class URLDecoderTest {
    @Test
    fun testEmpty() {
        test<Empty>("/") {
        }
    }

    @Test
    fun testConstant() {
        test<Constant>("/constant") {
        }
    }

    @Test
    fun testSingleParameter() {
        test<SingleParameter>("/value") {
            assertEquals("value", param)
        }
        test<SingleParameter>("/other") {
            assertEquals("other", param)
        }
    }

    @Test
    fun testParameterTwice() {
        test<ParameterTwice>("/aaa/aaa") {
            assertEquals("aaa", param)
        }

        test<ParameterTwice>("/1/2") {
            assertEquals("1", param)
        }
    }

    @Test
    fun testEllipsis() {
        test<Ellipsis>("/a/b/c") {
            assertEquals(listOf("a", "b", "c"), param)
        }
    }

    @Test
    fun testRootAndChild() {
        test<Root.Child>("/root/child/value") {
            assertEquals("value", param)
        }
        test<Root.Child>("/root/child/other") {
            assertEquals("other", param)
        }
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    private inline fun <reified T> test(actualPath: String, block: T.() -> Unit) {
        val decoder = URLDecoder(EmptyModule, Url("http://localhost$actualPath"))
        block(serializer<T>().deserialize(decoder))
    }

    @Serializable
    @Location("/")
    class Empty

    @Serializable
    @Location("/constant")
    class Constant

    @Serializable
    @Location("/{param}")
    data class SingleParameter(val param: String)

    @Serializable
    @Location("/{param}/{param}")
    data class ParameterTwice(val param: String)

    @Serializable
    @Location("/{param...}")
    data class Ellipsis(val param: List<String>)

    @Serializable
    @Location("/root")
    class Root {
        @Serializable
        @Location("/child/{param}")
        class Child(val param: String, val root: Root)
    }
}
