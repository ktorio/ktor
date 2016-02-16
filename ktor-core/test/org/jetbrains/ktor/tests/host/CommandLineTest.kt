package org.jetbrains.ktor.tests.host

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.junit.*
import java.net.*
import kotlin.test.*

class CommandLineTest {
    @Test
    fun testEmpty() {
        commandLineConfig(emptyArray())
    }

    @Test
    fun testChangePort() {
        assertEquals(13698, commandLineConfig(arrayOf("-port=13698")).first.port)
    }

    @Test
    fun testAmendConfig() {
        assertEquals(13698, commandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).first.port)
    }

    @Test
    fun testChangeHost() {
        assertEquals("test-server", commandLineConfig(arrayOf("-host=test-server")).first.host)
    }

    @Test
    fun testSingleArgument() {
        commandLineConfig(arrayOf("-it-should-be-no-effect"))
    }

    @Test
    fun testJar() {
        val jar = findContainingZipFile(CommandLineTest::class.java.classLoader.getResources("java/util/ArrayList.class").nextElement().toURI())
        val urlClassLoader = commandLineConfig(arrayOf("-jar=${jar.absolutePath}")).second.classLoader as URLClassLoader
        assertEquals(jar.toURI(), urlClassLoader.urLs.single().toURI())
    }
}