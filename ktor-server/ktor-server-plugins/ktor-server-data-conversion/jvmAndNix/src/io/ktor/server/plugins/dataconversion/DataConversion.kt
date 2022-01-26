/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.dataconversion

import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.converters.ConversionService
import io.ktor.util.converters.DataConversion
import io.ktor.util.converters.DefaultConversionService

/**
 * Object for installing [io.ktor.util.converters.DataConversion] plugin
 */
public object DataConversion :
    ApplicationPlugin<ApplicationCallPipeline, DataConversion.Configuration, DataConversion> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: DataConversion.Configuration.() -> Unit
    ): DataConversion {
        val configuration = DataConversion.Configuration().apply(configure)
        return DataConversion(configuration)
    }

    override val key: AttributeKey<DataConversion> = AttributeKey("DataConversion")
}

/**
 * Lookup for a conversion service. Returns the default one if the plugin wasn't installed
 */
@Suppress("DEPRECATION_ERROR")
public val ApplicationCallPipeline.conversionService: ConversionService
    get() = pluginOrNull(DataConversion) ?: DefaultConversionService
