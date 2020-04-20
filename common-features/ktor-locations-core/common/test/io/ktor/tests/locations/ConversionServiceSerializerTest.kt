/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*
import kotlin.test.*

@OptIn(ImplicitReflectionSerializer::class)
class ConversionServiceSerializerTest {

    @Location("/path/{uuid}")
    @Serializable
    class Parent(
        @ContextualSerialization
        val inner: C
    )

    @Location("/path/{uuid}")
    @Serializable
    class Path(
        val inner: List<String>
    )

    class C(val i: String)

    @Serializable
    class UUID(
        @ContextualSerialization
        val text: String
    )

    object ConverterForC : ConversionService {
        override fun toValues(value: Any?): List<String> {
            return listOf((value as C).i)
        }

        override fun fromValues(values: List<String>, type: KType): Any? {
            return C(values.single())
        }

        @OptIn(ExperimentalStdlibApi::class)
        override fun supportedTypes(): List<KType> {
            return listOf(typeOf<C>())
        }
    }

    @Test
    fun testC() {
        assertEquals("{\"inner\":[\"test\"]}", json<C, Parent>(Parent(C("test")), ConverterForC))
        assertEquals("inner=test", url<C, Parent>(Parent(C("test")), ConverterForC))
    }

    @Test
    fun testPath() {
        assertEquals("{\"inner\":[\"1\",\"2\"]}", json<C, Path>(Path(listOf("1", "2")), ConverterForC))
        assertEquals("inner=1&inner=2", url<C, Path>(Path(listOf("1", "2")), ConverterForC))
    }

    private inline fun <reified T : Any, reified P : Any> json(
        instance: P,
        conversionService: ConversionService
    ): String {
        return Json(context = moduleFor<T>(conversionService)).stringify(instance)
    }

    private inline fun <reified T : Any, reified P : Any> url(
        instance: P,
        conversionService: ConversionService
    ): String {
        val encoder = URLEncoder(moduleFor<T>(conversionService), P::class)
        val serializer = encoder.context.getContextualOrDefault(P::class)

        serializer.serialize(encoder, instance)

        return Parameters.build {
            encoder.buildTo(this)
        }.formUrlEncode()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private inline fun <reified T> moduleFor(conversionService: ConversionService): SerialModule {
        return moduleFor(typeOf<T>(), conversionService)
    }

    private fun moduleFor(type: KType, conversionService: ConversionService): SerialModule {
        return SerializersModule {
            conversionService(type, conversionService)
        }
    }

    private fun SerializersModuleBuilder.conversionService(type: KType, conversionService: ConversionService) {
        contextual(
            type.classifier as KClass<*>,
            ConverterSerializer(type, conversionService)
        )
    }
}
