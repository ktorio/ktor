/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.util.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * Data conversion feature to serialize and deserialize types using [converters] registry
 */
public class DataConversion(private val converters: Map<Type, ConversionService>) : ConversionService {
    override fun fromValues(values: List<String>, type: Type): Any? {
        val converter = converters[type] ?: DefaultConversionService
        return converter.fromValues(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val type: Type = value?.javaClass ?: return listOf()
        val converter = converters[type] ?: DefaultConversionService
        return converter.toValues(value)
    }

    /**
     * Data conversion service configuration
     */
    public class Configuration {
        internal val converters = mutableMapOf<Type, ConversionService>()

        /**
         * Register a [convertor] for [klass] type
         */
        public fun convert(klass: KClass<*>, convertor: ConversionService) {
            converters.put(klass.java, convertor)
        }

        /**
         * Register a [convertor] for [ktype] type
         */
        public fun convert(ktype: KType, convertor: ConversionService) {
            converters.put(ktype.javaType, convertor)
        }

        /**
         * Register and [configure] convertor for type [klass]
         */
        public fun convert(klass: KClass<*>, configure: DelegatingConversionService.() -> Unit) {
            convert(klass, DelegatingConversionService(klass).apply(configure))
        }

        /**
         * Register and [configure] convertor for reified type [T]
         */
        public inline fun <reified T> convert(noinline configure: DelegatingConversionService.() -> Unit): Unit =
            convert(T::class, configure)
    }

    /**
     * Object for installing feature
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, DataConversion> {
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): DataConversion {
            val configuration = Configuration().apply(configure)
            return DataConversion(configuration.converters)
        }

        override val key: AttributeKey<DataConversion> = AttributeKey("DataConversion")
    }
}

/**
 * Custom convertor builder
 */
public class DelegatingConversionService internal constructor(private val klass: KClass<*>) : ConversionService {
    private var decoder: ((values: List<String>, type: Type) -> Any?)? = null
    private var encoder: ((value: Any?) -> List<String>)? = null

    /**
     * Configure decoder function. Only one decoder could be supplied
     * @throws IllegalStateException
     */
    public fun decode(converter: (values: List<String>, type: Type) -> Any?) {
        if (decoder != null) throw IllegalStateException("Decoder has already been set for type '$klass'")
        decoder = converter
    }

    /**
     * Configure encoder function. Only one encoder could be supplied
     * @throws IllegalStateException
     */
    public fun encode(converter: (value: Any?) -> List<String>) {
        if (encoder != null) throw IllegalStateException("Encoder has already been set for type '$klass'")
        encoder = converter
    }

    override fun fromValues(values: List<String>, type: Type): Any? {
        val decoder = decoder ?: throw DataConversionException("Decoder was not specified for class '$klass'")
        return decoder(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val encoder = encoder ?: throw DataConversionException("Encoder was not specified for class '$klass'")
        return encoder(value)
    }
}

/**
 * Lookup for a conversion service. Returns the default one if the feature wasn't installed
 */
public val ApplicationCallPipeline.conversionService: ConversionService
    get() = featureOrNull(DataConversion) ?: DefaultConversionService
