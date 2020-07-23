/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.moshi

import com.squareup.moshi.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.ContentTransformationException
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import okio.*
import kotlin.reflect.jvm.*

/**
 * Moshi converter for [ContentNegotiation] feature
 */
class MoshiConverter(private val moshi: Moshi = Moshi.Builder().build()) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        return TextContent(moshi.adapter<Any>(value.javaClass).toJson(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val source = channel.toInputStream().source().buffer()
        val type = request.typeInfo.javaType

        return moshi.adapter<Any>(type).fromJson(source) ?: throw UnsupportedNullValuesException()
    }
}

/**
 * Register Moshi to [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.moshi(
    contentType: ContentType = ContentType.Application.Json,
    block: Moshi.Builder.() -> Unit = {}
) {
    val builder = Moshi.Builder()
    builder.apply(block)
    moshi(contentType, builder.build())
}

/**
 * Register Moshi to [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.moshi(
    contentType: ContentType = ContentType.Application.Json,
    moshi: Moshi
) {
    register(contentType, MoshiConverter(moshi))
}

internal class UnsupportedNullValuesException :
    ContentTransformationException("Receiving null values is not supported")
