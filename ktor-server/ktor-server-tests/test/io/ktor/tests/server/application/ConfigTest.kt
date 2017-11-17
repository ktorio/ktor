package io.ktor.tests.server.application

import io.ktor.config.*
import kotlin.test.*

class ConfigTest {
    @Test
    fun testMapApplicationConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a","b","c"))

        val auth = mapConfig.config("auth")
        assertEquals("ktor", auth.property("salt").getString())
        val users = auth.configList("users")
        assertEquals(1, users.size)
        assertEquals("test", users[0].property("name").getString())

        assertEquals(listOf("a","b","c"), auth.property("listValues").getList())

        val values = auth.property("values").getList()
        assertEquals("[a, b]", values.toString())

        assertEquals(null, auth.propertyOrNull("missingProperty"))
        assertEquals("SHA-256", auth.propertyOrNull("hashAlgorithm")?.getString())
        assertEquals(listOf("a","b","c"), auth.propertyOrNull("listValues")?.getList())

        assertEquals(null, mapConfig.propertyOrNull("missingProperty"))
        assertEquals(null, mapConfig.propertyOrNull("auth.missingProperty"))
        assertEquals("SHA-256", mapConfig.propertyOrNull("auth.hashAlgorithm")?.getString())
        assertEquals(listOf("a","b","c"), mapConfig.propertyOrNull("auth.listValues")?.getList())
    }
}