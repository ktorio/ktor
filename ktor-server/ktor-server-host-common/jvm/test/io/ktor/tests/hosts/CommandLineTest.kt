package io.ktor.tests.hosts

import com.typesafe.config.*
import io.ktor.server.engine.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.model.*
import java.io.*
import java.net.*
import java.util.*
import kotlin.test.*

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
    fun testPropertyConfig() {
        System.setProperty("ktor.deployment.port", "1333")
        ConfigFactory.invalidateCaches()
        assertEquals(1333, commandLineEnvironment(emptyArray()).connectors.single().port)
        System.clearProperty("ktor.deployment.port")
        ConfigFactory.invalidateCaches()
    }

    @Test
    fun testPropertyConfigOverride() {
        System.setProperty("ktor.deployment.port", "1333")
        ConfigFactory.invalidateCaches()
        assertEquals(13698, commandLineEnvironment(arrayOf("-P:ktor.deployment.port=13698")).connectors.single().port)
        System.clearProperty("ktor.deployment.port")
        ConfigFactory.invalidateCaches()
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
        val port = commandLineEnvironment(arrayOf("-config=$configPath")).config.property("ktor.deployment.port").getString()
        assertEquals("8080", port)
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
            if (lookup.isFile) return listOf(lookup.absoluteFile.toURI().toURL()).let { Collections.enumeration<URL>(it) }
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