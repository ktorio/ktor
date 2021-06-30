package io.ktor.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.ContentConverter
import io.ktor.features.ContentNegotiation
import io.ktor.features.suitableCharset
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.request.ApplicationReceiveRequest
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import okio.BufferedSource
import okio.Okio
import java.io.InputStream

/**
 *    install(ContentNegotiation) {
 *       moshi()
 *    }
 *
 *    to configure the Moshi.Builder, you could use the following:
 *
 *    install(ContentNegotiation) {
 *        moshi {
 *            // From moshi-kotlin package
 *            add(KotlinJsonAdapterFactory())
 *        }
 *    }
 *
 *    to use an existing Moshi instance:
 *
 *    install(ContentNegotiation) {
 *        // use an existing, or create a custom moshi instance to fit your needs
 *        val myMoshi = Moshi.Builder().build()
 *        moshi(myMoshi)
 *    }
 */
class MoshiConverter(private val moshi: Moshi) : ContentConverter {
    private val outputAdapter: JsonAdapter<Any?> = moshi.adapter<Any>(Any::class.java).nullSafe()

    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent(outputAdapter.toJson(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject

        val type = request.type
        val value = request.value as? ByteReadChannel ?: return null

        val adapter: JsonAdapter<out Any> = moshi.adapter(type.javaObjectType)
        val source: BufferedSource = value.toInputStream().toBufferedSource()
        return adapter.fromJson(source)
    }
}

fun ContentNegotiation.Configuration.moshi(moshi: Moshi) {
    val converter = MoshiConverter(moshi)
    register(ContentType.Application.Json, converter)
}

inline fun ContentNegotiation.Configuration.moshi(block: Moshi.Builder.() -> Unit = {}) {
    moshi(Moshi.Builder().apply {
        block()
    }.build())
}

private fun InputStream.toBufferedSource(): BufferedSource = Okio.buffer(Okio.source(this))
