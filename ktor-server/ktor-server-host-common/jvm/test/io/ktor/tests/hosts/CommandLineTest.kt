/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.hosts

import io.ktor.server.engine.*
import org.junit.*
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.model.*
import java.io.*
import java.net.*
import java.util.*
import kotlin.test.*
import kotlin.test.Test

class CommandLineTest {

    @get:Rule
    var classLoader = IsolatedClassLoaderRule()

    @Test
    fun testEmpty() {
        commandLineEnvironment(emptyArray())
    }

    @Test
    fun testChangePort() {
        assertEquals(13698, commandLineEnvironment(arrayOf("-port=13698")).connectors.single().port)
    }

    @Test
    fun testAmendConfig() {
        assertEquals(13698, commandLineEnvironment(arrayOf("-P:ktor.deployment.port=13698")).connectors.single().port)
    }

    @Test
    fun testChangeHost() {
        assertEquals("test-server", commandLineEnvironment(arrayOf("-host=test-server")).connectors.single().host)
    }

    @Test
    fun testSingleArgument() {
        commandLineEnvironment(arrayOf("-it-should-be-no-effect"))
    }

    @Test
    fun testJar() {
        val (file, uri) = findContainingZipFileOrUri(
            CommandLineTest::class.java.classLoader.getResources("java/util/ArrayList.class").nextElement().toURI()
        )

        val opt = if (file != null) file.absolutePath else uri!!.toASCIIString()
        val expectedUri = uri ?: file!!.toURI()

        val urlClassLoader = commandLineEnvironment(arrayOf("-jar=$opt")).classLoader as URLClassLoader
        assertEquals(expectedUri, urlClassLoader.urLs.single().toURI())
    }

    @Test
    fun configFileWithEnvironmentVariables() {
        val configPath = CommandLineTest::class.java.classLoader.getResource("applicationWithEnv.conf").toURI().path
        val port = commandLineEnvironment(arrayOf("-config=$configPath"))
            .config.property("ktor.deployment.port").getString()
        assertEquals("8080", port)
    }

    @Test
    fun configYamlFile() {
        val configPath = CommandLineTest::class.java.classLoader.getResource("application.yaml").toURI().path
        val port = commandLineEnvironment(arrayOf("-config=$configPath"))
            .config.property("ktor.deployment.port").getString()
        assertEquals("8081", port)
    }

    @Test
    fun hoconConfigResource() {
        val port = commandLineEnvironment(arrayOf("-config=applicationWithEnv.conf"))
            .config.property("ktor.deployment.port").getString()
        assertEquals("8080", port)
    }

    @Test
    fun yamlConfigResource() {
        val port = commandLineEnvironment(arrayOf("-config=application.yaml"))
            .config.property("ktor.deployment.port").getString()
        assertEquals("8081", port)
    }

    @Test
    fun testListPropertiesConfig() {
        val args = arrayOf(
            "-P:array.first.0=first", "-P:array.first.1=second", "-P:array.first.2=third",
            "-P:array.second.0=1", "-P:array.second.1=2",
            "-P:array.third.0=zero"
        )
        val env = commandLineEnvironment(args)
        val firstList = env.config.property("array.first").getList()
        val secondList = env.config.property("array.second").getList()
        val thirdList = env.config.property("array.third").getList()
        assertEquals(3, firstList.size)
        assertEquals(2, secondList.size)
        assertEquals(1, thirdList.size)
        assertEquals("first", firstList[0])
        assertEquals("2", secondList[1])
        assertEquals("zero", thirdList[0])
    }

    private tailrec fun findContainingZipFileOrUri(uri: URI): Pair<File?, URI?> {
        if (uri.scheme == "file") {
            return Pair(File(uri.path.substringBefore("!")), null)
        } else if (uri.scheme == "jrt") {
            return Pair(null, uri)
        } else {
            return findContainingZipFileOrUri(URI(uri.rawSchemeSpecificPart))
        }
    }

    class IsolatedClassLoaderRule : TestRule {
        override fun apply(s: Statement, d: Description): Statement {
            return object : Statement() {
                override fun evaluate() {
                    withIsolatedClassLoader {
                        s.evaluate()
                    }
                }
            }
        }

        private fun withIsolatedClassLoader(block: (ClassLoader) -> Unit) {
            val classLoader = IsolatedResourcesClassLoader(
                File("ktor-server/ktor-server-host-common/test-resources").absoluteFile,
                block::class.java.classLoader
            )

            val oldClassLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = classLoader
            try {
                block(classLoader)
            } finally {
                Thread.currentThread().contextClassLoader = oldClassLoader
            }
        }
    }

    private class IsolatedResourcesClassLoader(val dir: File, parent: ClassLoader) : ClassLoader(parent) {
        override fun getResources(name: String): Enumeration<URL> {
            val lookup = File(dir, name)
            if (lookup.isFile) {
                return listOf(lookup.absoluteFile.toURI().toURL()).let { Collections.enumeration<URL>(it) }
            }
            return parent.getResources(name)
        }

        override fun getResource(name: String): URL? {
            val lookup = File(dir, name)
            if (lookup.isFile) return lookup.absoluteFile.toURI().toURL()
            return parent.getResource(name)
        }

        override fun getResourceAsStream(name: String): InputStream? {
            return getResource(name)?.openStream()
        }
    }
}
