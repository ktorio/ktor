package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import java.math.*
import kotlin.reflect.jvm.*
import kotlin.test.*

class DataConversionTest {
    @Test
    fun testDefaultConversion() = withTestApplication {
        val id = application.conversionService.fromValues(listOf("1"), Int::class.java)
        assertEquals(1, id)
    }


    private val expectedList = listOf(1, 2)

    @Test
    fun testDefaultConversionList() = withTestApplication {
        val type = this@DataConversionTest::expectedList.returnType.javaType
        val id = application.conversionService.fromValues(listOf("1", "2"), type)
        assertEquals(expectedList, id)
    }

    @Test
    fun testBigNumbers() = withTestApplication {
        val expected = "12345678901234567890"
        val v = application.conversionService.toValues(BigDecimal(expected))
        assertEquals(expected, v.single())
        assertEquals(BigDecimal(expected), application.conversionService.fromValues(v, BigDecimal::class.java))

        val v2 = application.conversionService.toValues(BigInteger(expected))
        assertEquals(expected, v2.single())
        assertEquals(BigInteger(expected), application.conversionService.fromValues(v2, BigInteger::class.java))
    }

    data class EntityID(val typeId: Int, val entityId: Int)

    @Test
    fun testInstalledConversion() = withTestApplication {
        application.install(DataConversion) {
            convert<EntityID> {
                decode { values, _ ->
                    val (typeId, entityId) = values.single().split('-').map { it.toInt() }
                    EntityID(typeId, entityId)
                }

                encode { value ->
                    when (value) {
                        null -> listOf()
                        is EntityID -> listOf("${value.typeId}-${value.entityId}")
                        else -> throw DataConversionException("Cannot convert $value as EntityID")
                    }
                }
            }
        }

        val id = application.conversionService.fromValues(listOf("42-999"), EntityID::class.java)
        assertEquals(EntityID(42, 999), id)
    }
}