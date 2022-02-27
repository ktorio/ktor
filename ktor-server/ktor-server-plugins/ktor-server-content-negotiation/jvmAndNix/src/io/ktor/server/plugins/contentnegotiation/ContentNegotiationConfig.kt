/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Configuration type for [ContentNegotiation] plugin
 */
@KtorDsl
public class ContentNegotiationConfig : Configuration {
    internal val registrations = mutableListOf<ConverterRegistration>()
    internal val acceptContributors = mutableListOf<AcceptHeaderContributor>()

    @PublishedApi
    internal val ignoredTypes: MutableSet<KClass<*>> = mutableSetOf(
        HttpStatusCode::class,
        String::class
    )

    /**
     * Checks that `ContentType` header value of the response suits `Accept` header value of the request
     */
    public var checkAcceptHeaderCompliance: Boolean = false

    /**
     * Registers a [contentType] to a specified [converter] with an optional [configuration] script for converter
     */
    public override fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit
    ) {
        val registration = ConverterRegistration(contentType, converter.apply(configuration))
        registrations.add(registration)
    }

    /**
     * Register a custom accepted content types [contributor]. A [contributor] function takes [ApplicationCall]
     * and a list of content types accepted according to [HttpHeaders.Accept] header or provided by the previous
     * contributor if exists. Result of this [contributor] should be a list of accepted content types
     * with quality. A [contributor] could either keep or replace input list of accepted content types depending
     * on use-case. For example a contributor taking `format=json` request parameter could replace the original
     * content types list with the specified one from the uri argument.
     * Note that the returned list of accepted types will be sorted according to quality using [sortedByQuality]
     * so a custom [contributor] may keep it unsorted and should not rely on input list order.
     */
    public fun accept(contributor: AcceptHeaderContributor) {
        acceptContributors.add(contributor)
    }

    /**
     * Add a type to the list of types that should be ignored by [ContentNegotiation].
     *
     * The list contains [HttpStatusCode] type by default.
     */
    public inline fun <reified T> ignoreType() {
        ignoredTypes.add(T::class)
    }
}
