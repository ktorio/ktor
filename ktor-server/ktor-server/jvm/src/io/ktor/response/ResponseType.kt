/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.response

import io.ktor.util.*
import io.ktor.util.reflect.*

private val ResponseTypeAttributeKey: AttributeKey<TypeInfo> = AttributeKey("ResponseTypeAttributeKey")

/**
 * Type of the response object that was passed in [respond] function.
 * Can be useful for custom serializations.
 */
public var ApplicationResponse.responseType: TypeInfo?
    get() = call.attributes.getOrNull(ResponseTypeAttributeKey)
    @InternalAPI set(value) {
        if (value != null) {
            call.attributes.put(ResponseTypeAttributeKey, value)
        } else {
            call.attributes.remove(ResponseTypeAttributeKey)
        }
    }
