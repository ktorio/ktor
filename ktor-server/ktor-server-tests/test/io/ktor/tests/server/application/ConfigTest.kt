package io.ktor.tests.server.application

class ConfigTest {
    @org.junit.Test
    fun testMapApplicationConfig() {
        val mapConfig = io.ktor.config.MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a","b","c"))

        val auth = mapConfig.config("auth")
        kotlin.test.assertEquals("ktor", auth.property("salt").getString())
        val users = auth.configList("users")
        kotlin.test.assertEquals(1, users.size)
        kotlin.test.assertEquals("test", users[0].property("name").getString())

        kotlin.test.assertEquals(listOf("a","b","c"), auth.property("listValues").getList())

        val values = auth.property("values").getList()
        kotlin.test.assertEquals("[a, b]", values.toString())
    }
}