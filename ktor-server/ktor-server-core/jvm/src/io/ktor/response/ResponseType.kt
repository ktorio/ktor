/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.response

import io.ktor.util.*
import kotlin.reflect.*

private val ResponseTypeAttributeKey: AttributeKey<KType> = AttributeKey("ResponseTypeAttributeKey")

/**
 * Type of the response object that was passed in [respond] function
 */
public var ApplicationResponse.responseType: KType?
    get() = call.attributes.getOrNull(ResponseTypeAttributeKey)
    set(value) {
        if (value != null) {
            call.attributes.put(ResponseTypeAttributeKey, value)
        } else {
            call.attributes.remove(ResponseTypeAttributeKey)
        }
    }
