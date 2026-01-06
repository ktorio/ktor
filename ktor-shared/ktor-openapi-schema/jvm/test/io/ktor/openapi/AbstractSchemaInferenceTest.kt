/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.test.*

abstract class AbstractSchemaInferenceTest(
    val inference: JsonSchemaInference
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            sequenceBlockIndent = 2,
        )
    )

    @Test
    fun `nested classes`() =
        assertSchemaMatches<Address>()

    @Test
    fun `enum inference`() =
        assertSchemaMatches<Color>()

    @Test
    fun `sealed type inference`() =
        assertSchemaMatches<Shape>()

    @Test
    fun `advanced container annotations`() =
        assertSchemaMatches<ContainerTestData>()

    @Test
    fun `logical operators`() =
        assertSchemaMatches<LogicalOperatorsData>()

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

    private inline fun <reified T : Any> assertSchemaMatches() {
        val schema = inference.jsonSchema<T>()
        val expected = readSchemaYaml<T>()
        assertEquals(expected, yaml.encodeToString(schema))
    }

    private inline fun <reified T> readSchemaYaml(): String {
        val expectedFileName = "/schema/${T::class.simpleName}.yaml"
        val resource = this.javaClass.getResource(expectedFileName)
            ?: error("Missing expected schema file: $expectedFileName")
        return resource.readText().trim()
    }
}

@Serializable
sealed interface Location

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
) : Location

@Serializable
data class Municipality(
    val city: String,
    val province: String? = null,
    val country: Country,
) : Location

@Serializable
data class Country(
    @JsonSchemaPattern("[A-Za-z'-,]+")
    val name: String,
    @JsonSchemaPattern("[A-Z]{3}")
    val code: String,
) : Location

@Serializable
enum class Color {
    RED,
    GREEN,
    BLUE
}

@Serializable
sealed class Shape {
    @Serializable
    data class Circle(val radius: Double) : Shape()

    @Serializable
    data class Rectangle(val width: Double, val height: Double) : Shape()
}

@Serializable
data class AnnotatedUser(
    @JsonSchemaDescription("The user's unique identifier")
    val id: Int,
    @JsonSchemaMinLength(3)
    @JsonSchemaMaxLength(20)
    @JsonSchemaPattern("^[a-z0-9_]+$")
    val username: String,
    @JsonSchemaEmail
    val email: String,
    @JsonSchemaReadOnly
    val createdAt: String
)

@Serializable
data class ContainerTestData(
    @JsonSchemaUniqueItems
    @JsonSchemaMinItems(1)
    val tags: List<String>,
    @JsonSchemaMaxProperties(5)
    val metadata: Map<String, String>
)

@Serializable
data class LogicalOperatorsData(
    @JsonSchemaOneOf(Address::class, Country::class)
    val location: Location,
    @JsonSchemaNot(Color::class)
    val nonColorValue: String
)

// Helper annotation for pattern test
@Target(AnnotationTarget.PROPERTY)
@JsonSchemaPattern(".+@.+\\..+")
annotation class JsonSchemaEmail
