/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.util.*

/**
 * A configuration for the [Compression] plugin.
 */
public data class CompressionOptions(
    /**
     * Provides access to a map of encoders.
     */
    val encoders: Map<String, CompressionEncoderConfig> = emptyMap(),
    /**
     * Conditions for all encoders.
     */
    val conditions: List<BaseCall.(OutgoingContent) -> Boolean> = emptyList()
)

/**
 * An encoder configuration for the [Compression] plugin.
 */
public data class CompressionEncoderConfig(
    /**
     * An encoder name matched against an entry in the `Accept-Encoding` header.
     */
    val name: String,
    /**
     * An encoder implementation.
     */
    val encoder: CompressionEncoder,
    /**
     * Conditions for an encoder.
     */
    val conditions: List<BaseCall.(OutgoingContent) -> Boolean>,
    /**
     * A priority of an encoder.
     */
    val priority: Double
)

/**
 * A configuration for the [Compression] plugin.
 */
@KtorDsl
public class CompressionConfig : ConditionsHolderBuilder {
    /**
     * Provides access to a map of encoders.
     */
    public val encoders: MutableMap<String, CompressionEncoderBuilder> = hashMapOf()

    override val conditions: MutableList<BaseCall.(OutgoingContent) -> Boolean> = arrayListOf()

    /**
     * Appends an encoder with the specified [name] and [block] configuration.
     */
    public fun encoder(
        name: String,
        encoder: CompressionEncoder,
        block: CompressionEncoderBuilder.() -> Unit = {}
    ) {
        require(name.isNotBlank()) { "encoder name couldn't be blank" }
        if (name in encoders) {
            throw IllegalArgumentException("Encoder $name is already registered")
        }

        encoders[name] = CompressionEncoderBuilder(name, encoder).apply(block)
    }

    /**
     * Appends the default configuration with the `gzip`, `deflate`, and `identity` encoders.
     */
    public fun default() {
        gzip()
        deflate()
        identity()
    }

    /**
     * Builds [CompressionOptions].
     */
    internal fun buildOptions(): CompressionOptions = CompressionOptions(
        encoders = encoders.mapValues { (_, builder) ->
            if (conditions.none() && builder.conditions.none()) {
                builder.defaultConditions()
            }

            builder.buildConfig()
        },
        conditions = conditions.toList()
    )

    /**
     * Builds [CompressionOptions]
     */
    @Deprecated(
        "This is going to become internal. " +
            "Please stop building it manually or file a ticket with explanation why do you need it.",
        level = DeprecationLevel.ERROR
    )
    public fun build(): CompressionOptions = buildOptions()
}

/**
 * A builder for conditions.
 */
public interface ConditionsHolderBuilder {
    /**
     * Preconditions applied to every response object to check if it should be compressed.
     */
    public val conditions: MutableList<BaseCall.(OutgoingContent) -> Boolean>
}

/**
 * A builder for compression encoder configuration.
 * @property name of encoder
 * @property encoder instance
 */
@Suppress("MemberVisibilityCanBePrivate")
public class CompressionEncoderBuilder internal constructor(
    public val name: String,
    public val encoder: CompressionEncoder
) : ConditionsHolderBuilder {
    /**
     * A list of conditions for this encoder
     */
    override val conditions: ArrayList<BaseCall.(OutgoingContent) -> Boolean> = arrayListOf()

    /**
     * A priority for this encoder.
     */
    public var priority: Double = 1.0

    /**
     * Builds the [CompressionEncoderConfig] instance
     */
    @Deprecated(
        "This is going to become internal. " +
            "Please stop building it manually or file a ticket with explanation why do you need it.",
        level = DeprecationLevel.ERROR
    )
    public fun build(): CompressionEncoderConfig = buildConfig()

    internal fun buildConfig(): CompressionEncoderConfig {
        return CompressionEncoderConfig(name, encoder, conditions.toList(), priority)
    }
}

/**
 * Appends the `gzip` encoder with the [block] configuration.
 */
public fun CompressionConfig.gzip(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("gzip", GzipEncoder, block)
}

/**
 * Appends the `deflate` encoder with the [block] configuration and the 0.9 priority.
 */
public fun CompressionConfig.deflate(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("deflate", DeflateEncoder) {
        priority = 0.9
        block()
    }
}

/**
 * Appends the `identity` encoder with the [block] configuration.
 */
public fun CompressionConfig.identity(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("identity", IdentityEncoder, block)
}

/**
 * Appends a custom condition to the encoder or the [Compression] configuration.
 * A predicate returns `true` when a response need to be compressed.
 * If at least one condition is not met, a response isn't compressed.
 *
 * Note that adding a single condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.condition(predicate: BaseCall.(OutgoingContent) -> Boolean) {
    conditions.add(predicate)
}

/**
 * Appends a minimum size condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single minimum size condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.minimumSize(minSize: Long) {
    condition { content -> content.contentLength?.let { it >= minSize } ?: true }
}

/**
 * Appends a content type condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.matchContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType ?: return@condition false
        mimeTypes.any { contentType.match(it) }
    }
}

/**
 * Appends a content type exclusion condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single match condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.excludeContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType =
            content.contentType ?: response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                ?: return@condition true

        mimeTypes.none { excludePattern -> contentType.match(excludePattern) }
    }
}

/**
 * Configures default compression options.
 */
private fun ConditionsHolderBuilder.defaultConditions() {
    excludeContentType(
        ContentType.Video.Any,
        ContentType.Image.JPEG,
        ContentType.Image.PNG,
        ContentType.Audio.Any,
        ContentType.MultiPart.Any,
        ContentType.Text.EventStream
    )

    minimumSize(DEFAULT_MINIMAL_COMPRESSION_SIZE)
}
