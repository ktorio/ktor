/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlin.reflect.*

/**
 * Data conversion feature to serialize and deserialize types using [converters] registry
 */
public class DataConversion(configuration: Configuration) : ConversionService {
    private val converters: Map<KClass<*>, ConversionService> = configuration.converters.toMap()

    override fun fromValues(values: List<String>, type: TypeInfo): Any? {
        val converter = converters[type.type] ?: DefaultConversionService
        return converter.fromValues(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val type: KClass<*> = value?.let { it::class } ?: return listOf()
        val converter = converters[type] ?: DefaultConversionService
        return converter.toValues(value)
    }

    /**
     * Data conversion service configuration
     */
    public class Configuration {
        internal val converters = mutableMapOf<KClass<*>, ConversionService>()

        /**
         * Register a [convertor] for [ktype] type
         */
        public fun convert(type: KClass<*>, convertor: ConversionService) {
            converters[type] = convertor
        }

        /**
         * Register and [configure] convertor for type [klass]
         */
        public fun convert(type: KType, configure: DelegatingConversionService.() -> Unit) {
            val klass = type.classifier as KClass<*>
            convert(klass, DelegatingConversionService(klass).apply(configure))
        }

        /**
         * Register and [configure] convertor for reified type [T]
         */
        @OptIn(ExperimentalStdlibApi::class)
        public inline fun <reified T> convert(noinline configure: DelegatingConversionService.() -> Unit): Unit =
            convert(typeOf<T>(), configure)
    }
}

/**
 * Custom convertor builder to be used in [DataConversion.Configuration]
 */
public class DelegatingConversionService internal constructor(private val kClass: KClass<*>) : ConversionService {

    private var decoder: ((values: List<String>, type: TypeInfo) -> Any?)? = null
    private var encoder: ((value: Any?) -> List<String>)? = null

    /**
     * Configure decoder function. Only one decoder could be supplied
     * @throws IllegalStateException
     */
    public fun decode(converter: (values: List<String>, type: TypeInfo) -> Any?) {
        if (decoder != null) throw IllegalStateException("Decoder has already been set for type '$kClass'")
        decoder = converter
    }

    /**
     * Configure encoder function. Only one encoder could be supplied
     * @throws IllegalStateException
     */
    public fun encode(converter: (value: Any?) -> List<String>) {
        if (encoder != null) throw IllegalStateException("Encoder has already been set for type '$kClass'")
        encoder = converter
    }

    override fun fromValues(values: List<String>, type: TypeInfo): Any? {
        val decoder = decoder ?: throw DataConversionException("Decoder was not specified for class '$kClass'")
        return decoder(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val encoder = encoder ?: throw DataConversionException("Encoder was not specified for class '$kClass'")
        return encoder(value)
    }
}
