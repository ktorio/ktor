/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MergedApplicationConfigJvmTest {

    @Test
    fun mergedConversion() {
        val stringMap = listOf(
            "conf.stringMap.first" to "one",
            "conf.stringMap.second" to "two",
            "conf.subMap.first.id" to "1",
            "conf.subMap.first.name" to "first",
            "conf.list.0.id" to "3",
            "conf.list.0.name" to "third",
        )
        val hoconText = """
            conf {
                stringMap {
                    first: two
                    third: three
                }
                intMap {
                    first: 1
                    second: 2
                }
                subMap {
                    second {
                        id: 2
                        name: second
                    }
                }
            }
        """.trimIndent()

        val mapConfig = MapApplicationConfig(stringMap)
        val hoconConfig = HoconApplicationConfig(ConfigFactory.parseString(hoconText))
        val configObject = mapConfig.mergeWith(hoconConfig).property("conf").getAs<ConfigObject>()

        assertEquals(
            mapOf(
                "first" to "two",
                "second" to "two",
                "third" to "three",
            ),
            configObject.stringMap
        )
        assertEquals(
            mapOf("first" to 1, "second" to 2),
            configObject.intMap
        )
        assertEquals(
            mapOf(
                "first" to SimpleObject(1, "first"),
                "second" to SimpleObject(2, "second"),
            ),
            configObject.subMap
        )
        assertEquals(
            listOf(SimpleObject(3, "third")),
            configObject.list
        )
    }

    @Test
    fun mergedConfigThrowsOnMissingPath() {
        val hocon = HoconApplicationConfig(ConfigFactory.parseString("ktor { deployment { port = 8080 } }"))
        // Map config has no "nonexistent.*" keys, so the lookup is delegated to the HOCON fallback.
        val merged = MapApplicationConfig("app.name" to "test").withFallback(hocon)

        val exception = assertFailsWith<ApplicationConfigurationException> { merged.config("nonexistent") }
        assertEquals("Path nonexistent not found.", exception.message)
        assertIs<ConfigException.Missing>(exception.cause)
    }

    @Serializable
    data class ConfigObject(
        val stringMap: Map<String, String>?,
        val intMap: Map<String, Int>?,
        val subMap: Map<String, SimpleObject>?,
        val list: List<SimpleObject>?,
    )

    @Serializable
    data class SimpleObject(
        val id: Long,
        val name: String,
    )
}
