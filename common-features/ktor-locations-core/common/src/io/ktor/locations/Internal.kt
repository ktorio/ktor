/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlinx.serialization.descriptors.*

internal fun checkInfo(logger: (String) -> Unit, locationName: String, parentInfo: LocationInfo) {
    if (parentInfo.parentParameter != null) {
        throw LocationRoutingException("Nested location '$locationName' should have parameter for parent location because it is chained to its parent")
    }

    if (parentInfo.pathParameters.any { !it.isOptional }) {
        throw LocationRoutingException(
            "Nested location '$locationName' should have parameter for parent location because of non-optional path parameters ${parentInfo.pathParameters
                .filter { !it.isOptional }}"
        )
    }

    if (parentInfo.queryParameters.any { !it.isOptional }) {
        throw LocationRoutingException(
            "Nested location '$locationName' should have parameter for parent location because of non-optional query parameters ${parentInfo.queryParameters
                .filter { !it.isOptional }}"
        )
    }

    if (!parentInfo.isKotlinObject()) {
        logger("A nested location class should have a parameter with the type " +
            "of the outer location class. " +
            "See https://github.com/ktorio/ktor/issues/1660 for more details.")
    }
}

private fun LocationInfo.isKotlinObject(): Boolean =
    serialDescriptor.kind == StructureKind.OBJECT
