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
        val configPath = CommandLineTest::class.java.classLoader.getResource("applicationWithEnv.conf")!!.toURI().path
        val port = commandLineEnvironment(arrayOf("-config=$configPath"))
            .config.property("ktor.deployment.port").getString()
        assertEquals("8080", port)
    }

    @Test
    fun configYamlFile() {
        val configPath = CommandLineTest::class.java.classLoader.getResource("application.yaml")!!.toURI().path
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
    fun testListPropertiesHocon() {
        val args = arrayOf(
            "-P:array.first.0=first", "-P:array.first.1=second", "-P:array.first.2=third",
            "-P:array.second.0=1", "-P:array.second.1=2",
            "-P:array.third.0=zero"
        )
        val config = commandLineEnvironment(args).config
        val firstList = config.property("array.first").getList()
        val secondList = config.property("array.second").getList()
        val thirdList = config.property("array.third").getList()
        assertEquals(3, firstList.size)
        assertEquals(2, secondList.size)
        assertEquals(1, thirdList.size)
        assertEquals("first", firstList[0])
        assertEquals("2", secondList[1])
        assertEquals("zero", thirdList[0])
    }

    @Test
    fun testConfigListPropertiesHocon() {
        val args = arrayOf(
            "-P:users.0.name=test0", "-P:users.0.password=asd",
            "-P:users.1.name=test1", "-P:users.1.password=qwe",
            "-P:users.2.name=test2", "-P:users.2.password=zxc",
            "-P:users.2.groups.0=group0", "-P:users.2.groups.1=group1",
            "-P:users.2.tasks.0.id=id0", "-P:users.2.tasks.1.id=id1"
        )
        val config = commandLineEnvironment(args).config

        val users = config.configList("users")
        val groups = users[2].property("groups").getList()
        val tasks = users[2].configList("tasks")
        assertEquals(3, users.size)
        assertEquals(2, groups.size)
        assertEquals("test0", users[0].property("name").getString())
        assertEquals("qwe", users[1].property("password").getString())
        assertEquals("group0", groups[0])
        assertEquals("group1", groups[1])
        assertEquals("id0", tasks[0].property("id").getString())
        assertEquals("id1", tasks[1].property("id").getString())
    }

    @Test
    fun testAdditionalConfigFile() {
        val configPath = CommandLineTest::class.java.classLoader
            .getResource("application-main.conf")!!.toURI().path
        val additionalConfigPath = CommandLineTest::class.java.classLoader
            .getResource("application-additional.conf")!!.toURI().path
        val args = arrayOf("-config=$configPath", "-config=$additionalConfigPath")

        val config = commandLineEnvironment(args) {}.config

        assertEquals("<org.company.ApplicationClass>", config.property("ktor.application.class").getString())
        assertEquals("8085", config.property("ktor.deployment.port").getString())
        assertEquals("additional_value", config.property("ktor.property").getString())
        assertEquals("additional_value", config.property("ktor.config.property").getString())
        assertEquals("main_value", config.property("ktor.config.old_property").getString())
        assertEquals("additional_value", config.property("ktor.config.new_property").getString())

        assertEquals("additional_value", config.property("additional.config.property").getString())
        assertEquals("additional_value", config.config("additional").property("config.property").getString())
        assertEquals("additional_value", config.config("additional.config").property("property").getString())

        assertEquals("main_value", config.property("main.config.property").getString())
        assertEquals("main_value", config.config("main").property("config.property").getString())
        assertEquals("main_value", config.config("main.config").property("property").getString())
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
