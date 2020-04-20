/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.util.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Data conversion feature to serialize and deserialize types using [converters] registry
 */
class DataConversion internal constructor(
    private val converters: Map<Type, ConversionService>,
    private val convertersKTypes: Map<KType, ConversionService>
) : ConversionService {

    override fun supportedTypes(): List<KType> {
        return convertersKTypes.keys.toList() + DefaultConversionService.supportedTypes()
    }

    @Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR")
    override fun fromValues(values: List<String>, type: Type): Any? {
        val converter = converters[type] ?: DefaultConversionService
        return converter.fromValues(values, type)
    }

    override fun fromValues(values: List<String>, type: KType): Any? {
        val converter = convertersKTypes[type] ?: converters[type.toJavaType()] ?: DefaultConversionService
        return converter.fromValues(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val type: Type = value?.javaClass ?: return listOf()
        val converter = converters[type] ?: convertersKTypes[value::class.starProjectedType] ?: DefaultConversionService
        return converter.toValues(value)
    }

    /**
     * Data conversion service configuration
     */
    class Configuration {
        internal val converters = mutableMapOf<Type, ConversionService>()
        internal val convertersKTypes = mutableMapOf<KType, ConversionService>()

        /**
         * Register a [convertor] for [klass] type
         */
        fun convert(klass: KClass<*>, convertor: ConversionService) {
            converters[klass.java] = convertor
            convertersKTypes[klass.starProjectedType] = convertor
        }

        /**
         * Register a [convertor] for [ktype] type
         */
        fun convert(ktype: KType, convertor: ConversionService) {
            converters[ktype.javaType] = convertor
            convertersKTypes[ktype] = convertor
        }

        /**
         * Register a [convertor] for [ktype] type
         */
        fun convert(ktype: KType, configure: DelegatingConversionService.() -> Unit) {
            val converter = DelegatingConversionService(ktype).apply(configure)
            converters[ktype.toJavaType()] = converter
            convertersKTypes[ktype] = converter
        }

        /**
         * Register and [configure] convertor for type [klass]
         */
        fun convert(klass: KClass<*>, configure: DelegatingConversionService.() -> Unit) {
            convert(klass, DelegatingConversionService(klass.starProjectedType).apply(configure))
        }

        /**
         * Register and [configure] convertor for reified type [T]
         */
        @OptIn(ExperimentalStdlibApi::class)
        inline fun <reified T> convert(noinline configure: DelegatingConversionService.() -> Unit) =
            convert(typeOf<T>(), configure)
    }

    /**
     * Object for installing feature
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, DataConversion> {
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): DataConversion {
            val configuration = Configuration().apply(configure)
            return DataConversion(configuration.converters, configuration.convertersKTypes)
        }

        override val key = AttributeKey<DataConversion>("DataConversion")
    }
}

/**
 * Custom convertor builder
 */
class DelegatingConversionService internal constructor(private val kType: KType) : ConversionService {
    private var decoder: ((values: List<String>, type: Type) -> Any?)? = null
    private var decoderByKType: ((values: List<String>, type: KType) -> Any?)? = null
    private var encoder: ((value: Any?) -> List<String>)? = null

    override fun supportedTypes(): List<KType> {
        return listOf(kType)
    }

    /**
     * Configure decoder function. Only one decoder could be supplied
     * @throws IllegalStateException
     */
    @Deprecated("Use decodeByKType instead.")
    fun decode(converter: (values: List<String>, type: Type) -> Any?) {
        if (decoder != null) throw IllegalStateException("Decoder has already been set for type '$kType'")
        decoder = converter
    }

    /**
     * Configure decoder function. Only one decoder could be supplied
     * @throws IllegalStateException
     */
    fun decodeByKType(converter: (values: List<String>, type: KType) -> Any?) {
        if (decoder != null) throw IllegalStateException("Decoder has already been set for type '$kType'")
        decoderByKType = converter
    }

    /**
     * Configure encoder function. Only one encoder could be supplied
     * @throws IllegalStateException
     */
    fun encode(converter: (value: Any?) -> List<String>) {
        if (encoder != null) throw IllegalStateException("Encoder has already been set for type '$kType'")
        encoder = converter
    }

    @Suppress("OverridingDeprecatedMember")
    override fun fromValues(values: List<String>, type: Type): Any? {
        val decoder = decoder ?: throw DataConversionException("Decoder was not specified for class '$kType'")
        return decoder(values, type)
    }

    override fun fromValues(values: List<String>, type: KType): Any? {
        decoderByKType?.let { decoder ->
            return decoder(values, type)
        }
        decoder?.let { decoder ->
            return decoder(values, type.toJavaType())
        }

        throw DataConversionException("Decoder was not specified for class '$kType'")
    }

    override fun toValues(value: Any?): List<String> {
        val encoder = encoder ?: throw DataConversionException("Encoder was not specified for class '$kType'")
        return encoder(value)
    }
}

/**
 * Lookup for a conversion service. Returns the default one if the feature wasn't installed
 */
val ApplicationCallPipeline.conversionService: ConversionService
    get() = featureOrNull(DataConversion) ?: DefaultConversionService
