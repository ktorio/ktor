package org.jetbrains.ktor.tests

import org.jetbrains.ktor.host.*
import org.junit.*
import java.io.*
import java.net.*
import kotlin.test.*

class CommandLineTest {
    @Test
    fun testEmpty() {
        commandLineConfig(emptyArray())
    }

    @Test
    fun testChangePort() {
        assertEquals(13698, commandLineConfig(arrayOf("-port=13698")).first.connectors.single().port)
    }

    @Test
    fun testAmendConfig() {
        assertEquals(13698, commandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).first.connectors.single().port)
    }

    @Test
    fun testChangeHost() {
        assertEquals("test-server", commandLineConfig(arrayOf("-host=test-server")).first.connectors.single().host)
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

    tailrec
    private fun findContainingZipFile(uri: URI): File {
        if (uri.scheme == "file") {
            return File(uri.path.substringBefore("!"))
        } else {
            return findContainingZipFile(URI(uri.rawSchemeSpecificPart))
        }
    }
}