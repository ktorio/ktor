/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.util.*

@Deprecated(
    "[BadResponseStatusException] is deprecated. Use [ResponseException] instead.",
    ReplaceWith("ResponseException"),
    DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation")
public typealias BadResponseStatusException = ResponseException

@Deprecated(
    "Use [HttpCallValidator] instead.",
    ReplaceWith("HttpCallValidator"),
    DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation")
public class ExpectSuccess {
    @Suppress("DEPRECATION_ERROR")
    public companion object : HttpClientFeature<Unit, ExpectSuccess> {

        override val key: AttributeKey<ExpectSuccess>
            get() = error("Deprecated")

        override fun prepare(block: Unit.() -> Unit): ExpectSuccess {
            error("Deprecated")
        }

        override fun install(feature: ExpectSuccess, scope: HttpClient) {
            error("Deprecated")
        }
    }
}
