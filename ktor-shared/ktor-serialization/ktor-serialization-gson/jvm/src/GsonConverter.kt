/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.serialization.gson

import com.google.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * A content converter that uses [Gson]
 *
 * @param gson a configured instance of [Gson]
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 */
public class GsonConverter(
    private val gson: Gson = Gson(),
    private val streamRequestBody: Boolean = true
) : ContentConverter {

    @Deprecated(
        "Use GsonConverter(gson, streamRequestBody) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(gson: Gson = Gson()) : this(gson, true)

    @Suppress("OverridingDeprecatedMember")
    @Deprecated(
        "Please override and use serializeNullable instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("serializeNullable(charset, typeInfo, contentType, value)")
    )
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent {
        return serializeNullable(contentType, charset, typeInfo, value)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        if (!streamRequestBody) {
            // specific behavior for kotlinx.coroutines.flow.Flow : collect it into a List
            val resolvedValue = if (typeInfo.type == Flow::class) {
                (value as Flow<*>).toList()
            } else {
                value
            }
            return TextContent(gson.toJson(resolvedValue), contentType.withCharsetIfNeeded(charset))
        }
        return OutputStreamContent(
            {
                val writer = this.writer(charset = charset)
                // specific behavior for kotlinx.coroutines.flow.Flow
                if (typeInfo.type == Flow::class) {
                    if (prettyPrinting) {
                        // for pretty print we must collect the flow into a List
                        gson.toJson((value as Flow<*>).toList(), writer)
                    } else {
                        // emit asynchronous values in Writer : no pretty print here
                        (value as Flow<*>).serializeJson(writer)
                    }
                } else {
                    // non flow content
                    gson.toJson(value, writer)
                }

                // must flush manually
                writer.flush()
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        if (gson.isExcluded(typeInfo.type)) {
            throw ExcludedTypeGsonException(typeInfo.type)
        }

        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
                gson.fromJson(reader, typeInfo.reifiedType)
            }
        } catch (deserializeFailure: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found", deserializeFailure)
        }
    }

    private val prettyPrinting by lazy {
        val field = Gson::class.java.getDeclaredField("prettyPrinting")
        field.isAccessible = true
        field.get(gson) as Boolean
    }

    private companion object {
        private const val beginArrayCharCode = '['.code
        private const val endArrayCharCode = ']'.code
        private const val objectSeparator = ','.code
    }

    /**
     * Guaranteed to be called inside a [Dispatchers.IO] context, see [OutputStreamContent]
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <T> Flow<T>.serializeJson(writer: Writer) {
        writer.write(beginArrayCharCode)
        collectIndexed { index, value ->
            if (index > 0) {
                writer.write(objectSeparator)
            }
            gson.toJson(value, writer)
        }
        writer.write(endArrayCharCode)
    }
}

@Suppress("DEPRECATION")
internal fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExcludedTypeGsonException(
    private val type: KClass<*>
) : Exception("Type ${type.jvmName} is excluded so couldn't be used in receive"),
    CopyableThrowable<ExcludedTypeGsonException> {

    override fun createCopy(): ExcludedTypeGsonException = ExcludedTypeGsonException(type).also {
        it.initCause(this)
    }
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using GSON.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 */
@Deprecated("This will be removed.", level = DeprecationLevel.HIDDEN)
public fun Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    block: GsonBuilder.() -> Unit = {}
) {
    gson(contentType, true, block)
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using GSON.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 * @param contentType the content type to send with request
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 * @param block a configuration block for [Gson]
 */
public fun Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    streamRequestBody: Boolean = true,
    block: GsonBuilder.() -> Unit = {}
) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create(), streamRequestBody)
    register(contentType, converter)
}
