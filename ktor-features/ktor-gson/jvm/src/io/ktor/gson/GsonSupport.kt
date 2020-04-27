/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.gson

import com.google.gson.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.ContentTransformationException
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * GSON converter for [ContentNegotiation] feature
 */
class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        return TextContent(gson.toJson(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val reader = channel.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        val type = request.type

        if (gson.isExcluded(type)) {
            throw ExcludedTypeGsonException(type)
        }

        return gson.fromJson(reader, type.javaObjectType) ?: throw UnsupportedNullValuesException()
    }
}

/**
 * Register GSON to [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    block: GsonBuilder.() -> Unit = {}
) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create())
    register(contentType, converter)
}

internal class ExcludedTypeGsonException(
    val type: KClass<*>
) : Exception("Type ${type.jvmName} is excluded so couldn't be used in receive"),
    CopyableThrowable<ExcludedTypeGsonException> {

    override fun createCopy(): ExcludedTypeGsonException? = ExcludedTypeGsonException(type).also {
        it.initCause(this)
    }
}

internal class UnsupportedNullValuesException :
    ContentTransformationException("Receiving null values is not supported")

private fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)
