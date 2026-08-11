/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi.reflect

import io.ktor.openapi.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

annotation class CustomTypesDiscriminator(val property: String)
annotation class CustomSubTypes(vararg val value: CustomSubType)
annotation class CustomSubType(val value: KClass<*>, val name: String)

@CustomTypesDiscriminator("kind")
@CustomSubTypes(
    CustomSubType(CustomDiscriminatorShape.Circle::class, "CIRCLE"),
    CustomSubType(CustomDiscriminatorShape.Square::class, "SQUARE")
)
sealed interface CustomDiscriminatorShape {
    data class Circle(val radius: Int) : CustomDiscriminatorShape
    data class Square(val length: Int) : CustomDiscriminatorShape
}

class ReflectionJsonSchemaInferenceTest : AbstractSchemaInferenceTest(
    ReflectionJsonSchemaInference(object : SchemaReflectionAdapter {
        /**
         * Preserve property declaration order to match kotlinx-serialization output.
         */
        override fun <T : Any> getProperties(kClass: KClass<T>): Collection<KProperty1<T, *>> {
            val constructorParams = kClass.primaryConstructor?.parameters?.map { it.name } ?: return emptyList()
            val props = kClass.memberProperties.associateBy { it.name }
            return constructorParams.mapNotNull { props[it] }
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun getDiscriminatorProperty(kClass: KClass<*>): String =
            kClass.annotations.filterIsInstance<CustomTypesDiscriminator>().firstOrNull()?.property
                ?: kClass.annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator
                ?: super.getDiscriminatorProperty(kClass)

        override fun getDiscriminatorValue(kClass: KClass<*>, subclass: KClass<*>): String? {
            val subTypes = kClass.annotations.filterIsInstance<CustomSubTypes>().firstOrNull()
            return subTypes?.value?.firstOrNull { it.value == subclass }?.name
                ?: super.getDiscriminatorValue(kClass, subclass)
        }
    }),
    "reflect"
) {
    @Test
    fun `custom discriminator values test`() {
        val schema = inference.jsonSchema<CustomDiscriminatorShape>()
        assertNotNull(schema.discriminator)
        assertEquals("kind", schema.discriminator?.propertyName)
        val mapping = schema.discriminator?.mapping
        assertNotNull(mapping)
        assertEquals("#/components/schemas/io.ktor.openapi.reflect.CustomDiscriminatorShape.Circle", mapping["CIRCLE"])
        assertEquals("#/components/schemas/io.ktor.openapi.reflect.CustomDiscriminatorShape.Square", mapping["SQUARE"])
    }
}
