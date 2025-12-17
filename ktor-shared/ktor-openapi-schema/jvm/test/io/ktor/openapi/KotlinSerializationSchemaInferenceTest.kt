/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinSerializationSchemaInferenceTest {

    private val inference: JsonSchemaInference = KotlinxJsonSchemaInference

    @Test
    fun `basic class inference`() {
        val schema = inference.jsonSchema<Address>()
        assertEquals(JsonType.OBJECT, schema.type)
        assertEquals(
            mapOf(
                "houseNumber" to JsonType.INTEGER,
                "apartment" to JsonType.STRING,
                "street" to JsonType.STRING,
                "municipality" to JsonType.OBJECT,
                "postalCode" to JsonType.STRING
            ),
            schema.properties?.mapValues { (_, schemaRef) ->
                schemaRef.valueOrNull()?.type
            }
        )
        assertEquals(
            listOf(
                "houseNumber",
                "street",
                "municipality",
                "postalCode"
            ),
            schema.required
        )

        // Annotation-driven constraints on properties
        val houseNumber = schema.properties?.get("houseNumber")?.valueOrNull()
        assertNotNull(houseNumber)
        assertEquals(1.0, houseNumber.minimum)
        assertTrue(houseNumber.exclusiveMinimum != true) // should be null/false for exclusive=false

        val apartment = schema.properties["apartment"]?.valueOrNull()
        assertNotNull(apartment)
        assertEquals(1, apartment.minLength)
        assertEquals(4, apartment.maxLength)

        // Nested property annotations
        val municipality = schema.properties["municipality"]?.valueOrNull()
        assertNotNull(municipality)

        val country = municipality.properties?.get("country")?.valueOrNull()
        assertNotNull(country)

        val countryName = country.properties?.get("name")?.valueOrNull()
        assertNotNull(countryName)
        assertEquals("[A-Za-z'-,]+", countryName.pattern)

        val countryCode = country.properties["code"]?.valueOrNull()
        assertNotNull(countryCode)
        assertEquals("[A-Z]{3}", countryCode.pattern)
    }

    @Test
    fun `array inference`() {
        val schema = inference.jsonSchema<List<Address>>()
        val elementSchema = KotlinxJsonSchemaInference.jsonSchema<Address>()
        assertEquals(JsonType.ARRAY, schema.type)
        assertEquals(elementSchema, schema.items?.valueOrNull())
    }

    @Test
    fun `map inference`() {
        val schema = inference.jsonSchema<Map<String, Address>>()
        assertEquals(JsonType.OBJECT, schema.type)

        val additional = schema.additionalProperties
        assertNotNull(additional)

        val valueSchema = when (additional) {
            is AdditionalProperties.PSchema -> additional.value.valueOrNull()
            is AdditionalProperties.Allowed -> null
        }
        assertNotNull(valueSchema)
        assertEquals(JsonType.OBJECT, valueSchema.type)

        // Spot-check that the value schema is for Address-like structure (not a generic object).
        assertEquals(valueSchema.properties?.containsKey("street"), true)
        assertEquals(valueSchema.required?.contains("street"), true)
    }

    @Test
    fun `enum inference`() {
        val schema = inference.jsonSchema<Colour>()
        assertEquals(JsonType.STRING, schema.type)

        // Enum values are encoded as GenericElement<String> by the inference logic
        val values = schema.enum?.mapNotNull { it?.asA<String>() }
        assertEquals(listOf("RED", "GREEN", "BLUE"), values)
    }

    @Test
    fun `sealed type inference`() {
        val schema = inference.jsonSchema<Shape>()

        assertEquals(JsonType.OBJECT, schema.type)
        assertEquals("io.ktor.openapi.Shape", schema.title)
        assertEquals("type", schema.discriminator?.propertyName)
        assertEquals(mapOf(
            "io.ktor.openapi.Shape.Circle" to "#/components/schemas/Circle",
            "io.ktor.openapi.Shape.Rectangle" to "#/components/schemas/Rectangle"
        ), schema.discriminator?.mapping)
    }
}

@Serializable
data class Address(
    @JsonSchemaMinimum(value = 1.0, exclusive = false)
    val houseNumber: Int,
    @JsonSchemaMinLength(1)
    @JsonSchemaMaxLength(4)
    val apartment: String? = null,
    val street: String,
    val municipality: Municipality,
    val postalCode: String
)

@Serializable
data class Municipality(
    val city: String,
    val province: String? = null,
    val country: Country,
)

@Serializable
data class Country(
    @JsonSchemaPattern("[A-Za-z'-,]+")
    val name: String,
    @JsonSchemaPattern("[A-Z]{3}")
    val code: String,
)

// Test-only models for specific descriptor kinds
@Serializable
enum class Colour { RED, GREEN, BLUE }

@Serializable
sealed class Shape {
    @Serializable
    data class Circle(val radius: Double) : Shape()

    @Serializable
    data class Rectangle(val width: Double, val height: Double) : Shape()
}
