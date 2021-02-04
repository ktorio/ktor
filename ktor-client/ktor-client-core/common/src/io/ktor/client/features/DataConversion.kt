/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.util.*
import io.ktor.util.converters.DataConversion

/**
 * Object for installing [io.ktor.util.converters.DataConversion] as feature
 */
public object DataConversion : HttpClientFeature<DataConversion.Configuration, DataConversion> {
    override val key: AttributeKey<DataConversion> = AttributeKey("DataConversion")

    override fun prepare(block: DataConversion.Configuration.() -> Unit): DataConversion {
        val configuration = DataConversion.Configuration().apply(block)
        return DataConversion(configuration)
    }

    override fun install(feature: DataConversion, scope: HttpClient) {
        // no op
    }
}
