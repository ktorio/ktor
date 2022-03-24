/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.net.*
import kotlin.reflect.full.*
import kotlin.test.*

class OverridingClassLoaderTest {

    @Test
    fun childClassloaderDelegatesToParentForResources() {
        val thisClassLoader = javaClass.classLoader
        thisClassLoader.getResourceAsStream("resource.txt").use {
            // Check that it can be found if we load directly with the "parent" class loader
            checkNotNull(it)
        }
        // Ok. Then it should be possible to load it through the child class loader also.

        // Contstruct absolute classpath for the test source set
        val testClassesUrl = checkNotNull(javaClass.getResource("."))
//            .also { println(it) }
            .let {
                URL(
                    it.toString().replace(
                        "ktor/ktor-server/ktor-server-host-common/build/classes/kotlin/jvm/main/io/ktor/server/engine/",
                        "ktor/ktor-server/ktor-server-host-common/build/classes/kotlin/jvm/test/"
                    )
                )
            }

        val text = OverridingClassLoader(listOf(testClassesUrl), thisClassLoader).use {
            val childLoadedClassClazz = it.loadClass("io.ktor.server.engine.ChildLoadedClass")
            val expectedClassloaderPrefix = "io.ktor.server.engine.OverridingClassLoader\$ChildURLClassLoader"
            // Check it was loaded by the child class loader
            check(childLoadedClassClazz.classLoader.toString().startsWith(expectedClassloaderPrefix))

            // Great. Attempt to use it to load a resource we know exists
            val resourceStream = childLoadedClassClazz.kotlin.primaryConstructor!!
                .call("resource.txt")
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as () -> InputStream?
                }
                .invoke()

            resourceStream?.bufferedReader().use { it?.readText() }
        }

        assertEquals("resource example\n", text)
    }
}

/**
 * A class that loads resources as they generally do.
 */
@Suppress("UNUSED")
class ChildLoadedClass(
    private val resourceName: String,
) : () -> InputStream? {

    override fun invoke(): InputStream? {
        return javaClass.classLoader.getResourceAsStream(resourceName)
    }
}
