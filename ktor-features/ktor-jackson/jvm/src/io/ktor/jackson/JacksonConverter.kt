/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.jackson

import com.fasterxml.jackson.core.type.*
import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 *    install(ContentNegotiation) {
 *       register(ContentType.Application.Json, JacksonConverter())
 *    }
 *
 *    to be able to modify the objectMapper (eg. using specific modules and/or serializers and/or
 *    configuration options, you could use the following (as seen in the ktor-samples):
 *
 *    install(ContentNegotiation) {
 *        jackson {
 *            configure(SerializationFeature.INDENT_OUTPUT, true)
 *            registerModule(JavaTimeModule())
 +        }
 *    }
 */
class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {
    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent(objectmapper.writeValueAsString(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val value = request.value as? ByteReadChannel ?: return null
        val reader = value.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        return objectmapper.readValue(reader, KTypeRefAdapter(request.typeInfo))
    }
}

/**
 * Register Jackson converter into [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.jackson(contentType: ContentType = ContentType.Application.Json,
                                             block: ObjectMapper.() -> Unit = {}) {
    val mapper = jacksonObjectMapper()
    mapper.apply {
        setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
            indentObjectsWith(DefaultIndenter("  ", "\n"))
        })
    }
    mapper.apply(block)
    val converter = JacksonConverter(mapper)
    register(contentType, converter)
}

/**
 * Adapter from @see KType (which when produced from typeOf<T>() preserve generics type information)
 * to Jackson TypeReference (which is Jackson's implementation of typetoken to preserve generics type information)
 */
class KTypeRefAdapter(private val kType: KType) : TypeReference<Any>() {
    override fun getType(): Type {
        return kType.toJavaType()
    }
}

/**
 * Extracted from @see io.ktor.util.ReflectionUtils
 */
internal fun KType.toJavaType(): Type {
    val classifier = classifier

    return when {
        arguments.isNotEmpty() -> JavaTypeAdapter(this)
        classifier is KClass<*> -> classifier.javaObjectType
        classifier is KTypeParameter -> {
            error("KType parameter classifier is not supported")
        }
        else -> error("Unsupported type $this")
    }
}

private class JavaTypeAdapter(val type: KType) : ParameterizedType {
    override fun getRawType(): Type {
        return type.jvmErasure.javaObjectType
    }

    override fun getOwnerType(): Type? = null

    override fun getActualTypeArguments(): Array<Type> {
        return type.arguments.map {
            when (it.variance) {
                null, KVariance.IN, KVariance.OUT -> BoundTypeAdapter(it)
                else -> it.type!!.toJavaType()
            }
        }.toTypedArray()
    }
}

private class BoundTypeAdapter(val type: KTypeProjection) : WildcardType {
    override fun getLowerBounds(): Array<Type> {
        return when (type.variance) {
            null, KVariance.OUT -> arrayOf(Any::class.java)
            else -> arrayOf(type.type!!.toJavaType())
        }
    }

    override fun getUpperBounds(): Array<Type> {
        return when (type.variance) {
            null, KVariance.IN -> arrayOf(Any::class.java)
            else -> arrayOf(type.type!!.toJavaType())
        }
    }
}

