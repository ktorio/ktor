/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import io.ktor.resources.serialization.*
import io.ktor.utils.io.*
import kotlinx.serialization.modules.*

/**
 * Resources plugin instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.Resources)
 */
public class Resources(configuration: Configuration) {

    /**
     * The format instance used to (de)serialize resources instances
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.Resources.resourcesFormat)
     */
    public val resourcesFormat: ResourcesFormat = ResourcesFormat(configuration.serializersModule)

    /**
     * Configuration for the Resources plugin instance.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.Resources.Configuration)
     */
    @KtorDsl
    public class Configuration {

        /**
         * [SerializersModule] used to (de)serialize the Resource instances.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.Resources.Configuration.serializersModule)
         */
        public var serializersModule: SerializersModule = EmptySerializersModule()
    }
}
