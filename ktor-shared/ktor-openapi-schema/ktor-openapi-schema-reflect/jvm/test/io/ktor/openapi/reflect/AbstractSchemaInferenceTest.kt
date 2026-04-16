/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi.reflect

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.ktor.openapi.*
import io.ktor.openapi.JsonSchema.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class AbstractSchemaInferenceTest(
    val inference: JsonSchemaInference,
    val overrideKey: String,
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
    fun `other validation rules`() {
        assertSchemaMatches<AnnotatedUser>()
        @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
        assertExampleMatches(
            AnnotatedUser(
                id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                username = "Timber Calhoun",
                email = Email("tcalhoun@mail.com"),
                createdAt = Instant.parse("2023-02-03T23:23:23Z")
            )
        )
    }

    @Test
    fun `array inference`() {
        val actualListOfSchema = listOf(
            inference.jsonSchema<List<Address>>(),
            inference.jsonSchema<Array<Address>>()
        )
        val elementSchema = inference.jsonSchema<Address>()
        for (schema in actualListOfSchema) {
            assertEquals(JsonType.ARRAY, schema.type)
            assertEquals(elementSchema, schema.items?.valueOrNull())
        }
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
    fun `recursive inference`() {
        val schema = inference.jsonSchema<TreeNode>()
        val schemaYaml = yaml.encodeToString(schema)
        assertEquals(
            $$"""
                type: object
                title: io.ktor.openapi.reflect.TreeNode
                required:
                  - name
                properties:
                  name:
                    type: string
                  parent:
                    oneOf:
                      - $ref: "#/components/schemas/io.ktor.openapi.reflect.TreeNode"
                      - type: "null"
            """.trimIndent(),
            schemaYaml
        )
    }

    @Test
    open fun `unsigned types`() =
        assertSchemaMatches<UnsignedTypes>()

    @OptIn(ExperimentalTime::class)
    @Test
    open fun `time types`() {
        assertSchemaMatches<TimeTypes>()
        assertExampleMatches(
            TimeTypes(
                Instant.parse("2023-02-03T23:23:23Z"),
                17.days
            )
        )
    }

    @Test
    fun `value classes`() =
        assertSchemaMatches<Email>()

    private inline fun <reified T : Any> assertSchemaMatches() {
        val schema = inference.jsonSchema<T>()
        val expected = readSchemaYaml<T>()
        assertEquals(expected, yaml.encodeToString(schema))
    }

    private inline fun <reified T> readSchemaYaml(): String {
        val standardFile = "/schema/${T::class.simpleName}.yaml"
        val overrideFile = "/schema/${T::class.simpleName}.$overrideKey.yaml"
        val resource = this.javaClass.getResource(overrideFile)
            ?: this.javaClass.getResource(standardFile)
            ?: error("Missing expected schema file: $standardFile")
        return resource.readText().trim()
    }

    private inline fun <reified T> assertExampleMatches(value: T) {
        val expectedFile = "/schema/${T::class.simpleName}.example.yaml"
        val resource = this.javaClass.getResource(expectedFile)
            ?: error("Missing expected schema file: $expectedFile")
        assertEquals(resource.readText().trim(), yaml.encodeToString(value))
    }
}

@Serializable
sealed interface Location

@Serializable
data class Address(
    @Minimum(value = 1.0, exclusive = false)
    val houseNumber: Int,
    @MinLength(1)
    @MaxLength(4)
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
    @Pattern("[A-Za-z'-,]+")
    val name: String,
    @Pattern("[A-Z]{3}")
    val code: String,
) : Location

@Serializable
enum class Color {
    RED,
    GREEN,
    BLUE
}

@Serializable
data class UnsignedTypes(
    val unsignedInt: UInt,
    val unsignedLong: ULong
)

@OptIn(ExperimentalTime::class)
@Serializable
data class TimeTypes(
    val instant: Instant,
    val duration: Duration
)

@Serializable
sealed class Shape {
    @Serializable
    data class Circle(val radius: Double) : Shape()

    @Serializable
    data class Rectangle(val width: Double, val height: Double) : Shape()
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Serializable
data class AnnotatedUser(
    @Description("The user's unique identifier")
    val id: Uuid,
    @MinLength(3)
    @MaxLength(20)
    @Pattern("^[a-z0-9_]+$")
    val username: String,
    @Pattern(".+@.+\\..+")
    @Format("email")
    val email: Email,
    @ReadOnly
    val createdAt: Instant
)

@Serializable
data class ContainerTestData(
    @UniqueItems
    @MinItems(1)
    val tags: List<String>,
    @MaxProperties(5)
    val metadata: Map<String, String>
)

@Serializable
data class LogicalOperatorsData(
    @OneOf(Address::class, Country::class)
    val location: Location,
    @Not(Color::class)
    val nonColorValue: String
)

@Serializable
data class TreeNode(
    val name: String,
    val parent: TreeNode?
)

@JvmInline
@Serializable
value class Email(val value: String)
